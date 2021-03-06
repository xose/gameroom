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

import org.bson.BSONObject;

import es.udc.pfc.gamelib.chess.MiniChessGame;
import es.udc.pfc.xmpp.stanza.JID;

public final class MiniChessRoom extends ChessRoom {

	public MiniChessRoom(final GameComponent component, final JID roomJID) {
		super(component, roomJID, new MiniChessGame());
	}
	
	public MiniChessRoom(final GameComponent component, final BSONObject dbObject) {
		super(component, dbObject, new MiniChessGame());
	}
	
	@Override
	public final String getType() {
		return "minichess";
	}
	
	@Override
	protected final void updateSubject() {
		changeSubject("MiniChess");
	}

}
