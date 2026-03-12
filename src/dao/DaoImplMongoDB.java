package dao;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import org.bson.Document;

import model.Amount;
import model.Employee;
import model.Product;

public class DaoImplMongoDB implements Dao {

	private static final String DEFAULT_DATABASE_NAME = "shop";
	private static final String COLLECTION_INVENTORY = "inventory";
	private static final String COLLECTION_HISTORICAL_INVENTORY = "historical_inventory";
	private static final String COLLECTION_USERS = "users";
	private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";

	private MongoClient mongoClient;
	private MongoDatabase database;

	@Override
	public void connect() {
		if (mongoClient == null) {
			mongoClient = new MongoClient(new MongoClientURI(getMongoUri()));
			database = mongoClient.getDatabase(getDatabaseName());
		}
	}

	@Override
	public void disconnect() {
		if (mongoClient != null) {
			mongoClient.close();
			mongoClient = null;
			database = null;
		}
	}

	@Override
	public Employee getEmployee(int employeeId, String password) {
		ensureConnected();
		MongoCollection<Document> users = database.getCollection(COLLECTION_USERS);
		Document user = users
				.find(Filters.and(Filters.eq("employeeId", employeeId), Filters.eq("password", password)))
				.first();

		if (user == null) {
			return null;
		}

		String name = user.getString("name");
		if (name == null || name.isBlank()) {
			name = "employee-" + employeeId;
		}
		return new Employee(employeeId, name, password);
	}

	@Override
	public ArrayList<Product> getInventory() {
		ensureConnected();
		ArrayList<Product> inventory = new ArrayList<Product>();
		MongoCollection<Document> productsCollection = database.getCollection(COLLECTION_INVENTORY);
		FindIterable<Document> docs = productsCollection.find().sort(Sorts.ascending("id"));

		for (Document doc : docs) {
			int id = getIntValue(doc, "id", 0);
			String name = doc.getString("name");
			double price = getDoubleValue(doc, "price", getDoubleValue(doc, "wholesalerPrice", 0.0));
			boolean available = getBooleanValue(doc, "available", true);
			int stock = getIntValue(doc, "stock", 0);

			if (name == null) {
				continue;
			}

			Product product = new Product(id, name, new Amount(price), available, stock);
			inventory.add(product);
		}

		return inventory;
	}

	@Override
	public boolean writeInventory(ArrayList<Product> inventory) {
		if (inventory == null) {
			return false;
		}

		try {
			ensureConnected();
			MongoCollection<Document> historyCollection = database.getCollection(COLLECTION_HISTORICAL_INVENTORY);
			List<Document> historyRows = new ArrayList<Document>();
			Date now = new Date();

			for (Product product : inventory) {
				Document row = new Document("id_product", product.getId()).append("name", product.getName())
						.append("price", product.getWholesalerPrice().getValue())
						.append("available", product.isAvailable()).append("stock", product.getStock())
						.append("created_at", now);
				historyRows.add(row);
			}

			if (!historyRows.isEmpty()) {
				historyCollection.insertMany(historyRows);
			}
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}

	@Override
	public void addProduct(Product product) {
		ensureConnected();
		MongoCollection<Document> productsCollection = database.getCollection(COLLECTION_INVENTORY);
		Document doc = new Document("id", product.getId()).append("name", product.getName())
				.append("price", product.getWholesalerPrice().getValue()).append("available", product.isAvailable())
				.append("stock", product.getStock());
		productsCollection.insertOne(doc);
	}

	@Override
	public void updateProduct(Product product) {
		ensureConnected();
		MongoCollection<Document> productsCollection = database.getCollection(COLLECTION_INVENTORY);
		productsCollection.updateOne(Filters.eq("id", product.getId()),
				Updates.combine(Updates.set("name", product.getName()),
						Updates.set("price", product.getWholesalerPrice().getValue()),
						Updates.set("available", product.isAvailable()), Updates.set("stock", product.getStock())));
	}

	@Override
	public void deleteProduct(int productId) {
		ensureConnected();
		MongoCollection<Document> productsCollection = database.getCollection(COLLECTION_INVENTORY);
		productsCollection.deleteOne(Filters.eq("id", productId));
	}

	private void ensureConnected() {
		if (database == null) {
			connect();
		}
	}

	private String getMongoUri() {
		return System.getProperty("shop.mongodb.uri", DEFAULT_MONGO_URI);
	}

	private String getDatabaseName() {
		return System.getProperty("shop.mongodb.database", DEFAULT_DATABASE_NAME);
	}

	private int getIntValue(Document document, String key, int defaultValue) {
		Object value = document.get(key);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return defaultValue;
	}

	private double getDoubleValue(Document document, String key, double defaultValue) {
		Object value = document.get(key);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		return defaultValue;
	}

	private boolean getBooleanValue(Document document, String key, boolean defaultValue) {
		Object value = document.get(key);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		return defaultValue;
	}
	
}
