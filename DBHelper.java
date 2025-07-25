package ir.hakim.classes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.mongodb.client.model.Aggregates.*;

public class DBHelper {
    // disable logging
    static Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    static {
        root.setLevel(Level.ERROR);
    }

    private static final String dbName = "ghaemDb";
    private static final String connectionString = "mongodb://localhost:27017";

    public static List<Document> loadThisSensors(List<String> sensorIdsToLoad) {
        return loadSensors(sensorIdsToLoad, false);
    }

    public static List<Document> loadOtherSensors(List<String> sensorIdsToExclude) {
        return loadSensors(sensorIdsToExclude, true);
    }

    private static List<Document> loadSensors(List<String> lstIds, boolean exclude) {
        List<Document> docsList = null;
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection("sensors");

            Bson pipeline = lookup("sensorstypes", "sensorType", "_id", "sensorTypeDoc");

            // projection
            List<String> cols = new ArrayList<>(Arrays.asList(Constants.sensorsColumnNames));
            cols.remove("sensorType");
            Bson fields = Projections.fields(
                    Projections.include(cols),
                    Projections.computed("sensorType", "$sensorTypeDoc.latinName"));

            // load those sensors that are active, not deleted and not previously included
            Bson filters = Filters.and(
                    Filters.eq("active", true),
                    Filters.eq("isDeleted", false));
            
            if (lstIds != null && !lstIds.isEmpty()) {
                // change string ids to ObjectIds
                List<ObjectId> objectIds = new ArrayList<>();
                for (String id : lstIds) {
                    if (id != null && !id.trim().isEmpty()) {
                        try {
                            objectIds.add(new ObjectId(id));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid ObjectId format: " + id);
                        }
                    }
                }

                if (!objectIds.isEmpty()) {
                    if (exclude) {
                        filters = Filters.and(filters, Filters.nin("_id", objectIds)); // exclude these ids and load others
                    } else {
                        filters = Filters.and(filters, Filters.in("_id", objectIds)); // load these ids only
                    }
                }
            }

            docsList = collection.aggregate(Arrays.asList(
                            match(filters),
                            pipeline,
                            project(fields),
                            unwind("$sensorType"))) // for remove brackets around sensorType
                    .into(new ArrayList<>());
                    
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return docsList != null ? docsList : new ArrayList<>();
    }

    public static List<Document> loadCollection(String collectionName) {
        List<Document> docsList = new ArrayList<>();
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            docsList = collection.find().into(new ArrayList<>());
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return docsList;
    }

    public static boolean saveDoc(String collectionName, Document doc) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            
            // Add timestamp if not present
            if (!doc.containsKey("createdAt")) {
                doc.append("createdAt", new Date());
            }
            if (!doc.containsKey("uid")) {
                doc.append("uid", UUID.randomUUID().toString());
            }
            
            collection.insertOne(doc);
            return true;
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static boolean saveDoc(String collectionName, String jsonData) {
        try {
            Document doc = Document.parse(jsonData);
            return saveDoc(collectionName, doc);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static boolean updateDoc(String collectionName, String json, HashMap<String, Object> filters) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            
            BasicDBObject filter = getFilter(filters);
            Document setData = Document.parse(json);
            
            // Add update timestamp
            setData.append("updatedAt", new Date());
            
            Document update = new Document();
            update.append("$set", setData);
            
            long modifiedCount = collection.updateOne(filter, update).getModifiedCount();
            return modifiedCount > 0;
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static boolean deleteDoc(String collectionName, HashMap<String, Object> filters) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            
            BasicDBObject filter = getFilter(filters);
            long deletedCount = collection.deleteOne(filter).getDeletedCount();
            return deletedCount > 0;
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private static BasicDBObject getFilter(HashMap<String, Object> filters) {
        BasicDBObject filter = new BasicDBObject();
        if (filters != null) {
            for (Map.Entry<String, Object> f : filters.entrySet()) {
                if (f.getKey() != null && f.getValue() != null) {
                    filter.append(f.getKey(), f.getValue());
                }
            }
        }
        return filter;
    }

    /**
     * Check if database connection is available
     * @return true if connection is successful, false otherwise
     */
    public static boolean testConnection() {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            // Try to list collections to test connection
            db.listCollectionNames().first();
            return true;
        } catch (Exception ex) {
            System.out.println("Database connection test failed: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Get the count of documents in a collection
     * @param collectionName The name of the collection
     * @return The document count, or -1 if error
     */
    public static long getCollectionCount(String collectionName) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            return collection.countDocuments();
        } catch (Exception ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    /**
     * Check if a document exists with the given filters
     * @param collectionName The name of the collection
     * @param filters The filter criteria
     * @return true if document exists, false otherwise
     */
    public static boolean documentExists(String collectionName, HashMap<String, Object> filters) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            
            BasicDBObject filter = getFilter(filters);
            return collection.countDocuments(filter) > 0;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}