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
                ObjectId[] ids = new ObjectId[lstIds.size()];

                for (int i = 0; i < lstIds.size(); i++) {
                    ids[i] = new ObjectId(lstIds.get(i));
                }

                if (exclude)
                    filters = Filters.and(filters, Filters.nin("_id", ids)); // exclude these ids and load others
                else
                    filters = Filters.and(filters, Filters.in("_id", ids)); // load these ids only
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
        return docsList;
    }

    public static List<Document> loadCollection(String collectionName) {
        List<Document> docsList;
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            docsList = collection.find().into(new ArrayList<>());
        }
        return docsList;
    }

    public static void saveDoc(String collectionName, Document doc) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            collection.insertOne(doc);
        }
    }

    public static void saveDoc(String collectionName, String jsonData) {
        saveDoc(collectionName, Document.parse(jsonData));
    }

    public static void updateDoc(String collectionName, String json, HashMap<String, Object> filters) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            BasicDBObject filter = getFilter(filters);
            Document setData = Document.parse(json);
            Document update = new Document();
            update.append("$set", setData);
            collection.updateOne(filter, update);
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void deleteDoc(String collectionName, HashMap<String, Object> filters) {
        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            MongoCollection<Document> collection = db.getCollection(collectionName);
            collection.deleteOne(getFilter(filters));
        } catch (MongoTimeoutException e) {
            System.out.println("!!! could not connect to database !!!");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static BasicDBObject getFilter(HashMap<String, Object> filters) {
        BasicDBObject filter = new BasicDBObject();
        for (Map.Entry<String, Object> f : filters.entrySet()) {
            filter.append(f.getKey(), f.getValue());
        }
        return filter;
    }
}