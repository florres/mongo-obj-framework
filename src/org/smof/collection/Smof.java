package org.smof.collection;

import java.io.Closeable;

import org.bson.BsonDocument;

import org.smof.element.Element;
import org.smof.exception.SmofException;
import org.smof.parsers.SmofParser;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

@SuppressWarnings("javadoc")
public class Smof implements Closeable {

	@Deprecated
	public static Smof create(MongoDatabase database) {
		return new Smof(null, database);
	}
	
	public static Smof create(String host, int port, String database) {
		final MongoClient client = new MongoClient(host, port);
		return new Smof(client, client.getDatabase(database));
	}

	private final MongoDatabase database;
	private final CollectionsPool collections;
	private final SmofParser parser;
	private final SmofDispatcher dispatcher;
	private final MongoClient client;

	private Smof(MongoClient client, MongoDatabase database) {
		this.client = client;
		this.database = database;
		this.collections = new CollectionsPool();
		this.dispatcher = new SmofDispatcher(collections);
		this.parser = new SmofParser(dispatcher);
	}

	public <T extends Element> void loadCollection(String collectionName, Class<T> elClass) {
		loadCollection(collectionName, elClass, CollectionOptions.create());
	}
	
	public <T extends Element> void loadCollection(String collectionName, Class<T> elClass, CollectionOptions<T> options) {
		parser.registerType(elClass);
		loadCollection(collectionName, elClass, parser, options);
	}

	public <T extends Element> void loadCollection(String collectionName, Class<T> elClass, Object factory) {
		loadCollection(collectionName, elClass, factory, CollectionOptions.create());
	}
	
	public <T extends Element> void loadCollection(String collectionName, Class<T> elClass, Object factory, CollectionOptions<T> options) {
		parser.registerType(elClass, factory);
		loadCollection(collectionName, elClass, parser, options);
	}

	private <T extends Element> void loadCollection(String collectionName, Class<T> elClass, SmofParser parser, CollectionOptions<T> options) {
		final MongoCollection<BsonDocument> collection = database.getCollection(collectionName, BsonDocument.class);
		collections.put(elClass, new SmofCollectionImpl<T>(collectionName, collection, elClass, parser, options));
	}

	public <T> void registerSmofObject(Class<T> type) throws SmofException {
		parser.registerType(type);
	}

	public <T> void registerSmofFactory(Class<T> type, Object factory) throws SmofException {
		parser.registerType(type, factory);
	}

	public <T extends Element> void createCollection(String collectionName, Class<T> elClass) {
		createCollection(collectionName, elClass, CollectionOptions.create());
	}
	
	public <T extends Element> void createCollection(String collectionName, Class<T> elClass, CollectionOptions<T> options) {
		database.createCollection(collectionName);
		loadCollection(collectionName, elClass, options);
	}

	public <T extends Element> void createCollection(String collectionName, Class<T> elClass, Object factory) {
		createCollection(collectionName, elClass, factory, CollectionOptions.create());
	}
	
	public <T extends Element> void createCollection(String collectionName, Class<T> elClass, Object factory, CollectionOptions<T> options) {
		database.createCollection(collectionName);
		loadCollection(collectionName, elClass, factory, options);
	}

	public boolean dropCollection(String collectionName) {
		SmofCollection<?> toDrop = null;
		for(SmofCollection<?> collection : collections) {
			if(collectionName.equals(collection.getCollectionName())) {
				toDrop = collection;
				break;
			}
		}
		if(toDrop != null) {
			toDrop.getMongoCollection().drop();
			collections.remove(toDrop);
			return true;
		}
		return false;
	}

	public <T extends Element> void insert(T element) {
		dispatcher.insert(element);
		parser.reset();
	}
	
	public <T extends Element> SmofUpdate<T> update(Class<T> elementClass) {
		final SmofCollection<T> collection = collections.getCollection(elementClass);
		return collection.update();
	}

	public <T extends Element> SmofQuery<T> find(Class<T> elementClass) {
		final SmofCollection<T> collection = collections.getCollection(elementClass);
		return collection.query();
	}

	@Override
	public void close() {
		if(client != null) {
			client.close();
		}
	}

}
