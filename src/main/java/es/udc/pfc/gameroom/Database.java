/**
 * Copyright 2011 José Martínez
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package es.udc.pfc.gameroom;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

public final class Database {
	
	private static final DBCollection games;
	
	static {
		try {
			final DB db = new Mongo().getDB("gameroom");
			games = db.getCollection("games");
			games.ensureIndex("type");
			games.ensureIndex("finished");
		} catch (Exception e) {
			throw new RuntimeException("Error initializing Mongo", e);
		}
	}
	
	private Database() {
	}
	
	public static final void saveGame(final DBObject object) {
		games.save(object);
	}
	
	public static final Iterable<DBObject> getOpenGames() {
		final DBObject query = new BasicDBObject();
		query.put("finished", false);
		
		return games.find(query);
	}
	
}
