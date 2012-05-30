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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;

import es.udc.pfc.gamelib.board.Position;
import es.udc.pfc.gamelib.chess.ChessColor;
import es.udc.pfc.gamelib.chess.ChessGame;
import es.udc.pfc.gamelib.chess.ChessMovement;
import es.udc.pfc.xmpp.stanza.JID;
import es.udc.pfc.xmpp.stanza.Message;
import es.udc.pfc.xmpp.xml.XMLElement;

public abstract class ChessRoom extends AbstractRoom {
	
	protected final ChessGame chessGame;
	
	private boolean joinable;

	protected ChessRoom(final GameComponent component, final JID roomJID, final ChessGame chessGame) {
		super(component, roomJID);
		this.chessGame = checkNotNull(chessGame);
		joinable = true;
	}
	
	protected ChessRoom(final GameComponent component, final BSONObject dbObject, final ChessGame chessGame) {
		super(component, dbObject);
		this.chessGame = checkNotNull(chessGame);
		
		final BasicBSONList moves = (BasicBSONList)dbObject.get("moves");
		for (final Object moveObj : moves) {
			final BasicBSONObject move = (BasicBSONObject) moveObj;
			
			if (chessGame.movePiece(Position.fromString(move.getString("from")), Position.fromString(move.getString("to"))) == null)
				throw new IllegalArgumentException("Invalid movement");
		}
		
		joinable = Boolean.FALSE.equals(dbObject.get("finished"));
	}
	
	@Override
	protected void buildBSONObject(final BSONObject result) {
		final List<Object> moves = new BasicBSONList();
		for (ChessMovement movement : chessGame.getMovements()) {
			final BSONObject move = new BasicBSONObject();
			move.put("from", movement.getFrom().toString());
			move.put("to", movement.getTo().toString());
			moves.add(move);
		}
		result.put("moves", moves);
		
		result.put("finished", chessGame.isFinished());
		if (chessGame.isFinished()) {
			result.put("winner", chessGame.getWinner() != null ? chessGame.getWinner().toString() : "tie");
		}
	}
	
	@Override
	protected final String getXMLNS() {
		return "urn:xmpp:gamepfc:chess";
	}

	@Override
	public final boolean joinable() {
		return joinable;
	}

	@Override
	protected final void commandReceived(final JID user, final XMLElement x) throws Exception {
		if (players.size() != 2) 
			throw new Exception("not-started");
		
		if (x.hasChild("move")) {
			final XMLElement xmove = x.getFirstChild("move");
			
			if (playerColor(user) != chessGame.getCurrentTurn())
				throw new Exception("invalid-turn");

			final Position from = Position.fromString(xmove.getAttribute("from"));
			final Position to = Position.fromString(xmove.getAttribute("to"));

			if (from == null || to == null)
				throw new Exception("invalid-position");

			final ChessMovement move = chessGame.movePiece(from, to);
			if (move == null)
				throw new Exception("invalid-movement");
			
			final Message result = new Message();
			final XMLElement xr = result.addExtension("x", getXMLNS());
			final XMLElement xrmove = xr.addChild("move");
			xrmove.setAttribute("from", move.getFrom().toString());
			xrmove.setAttribute("to", move.getTo().toString());
			
			if (chessGame.isFinished()) {
				if (chessGame.getWinner() != null) {
					xr.addChild("winner").setAttribute("color", chessGame.getWinner().name());
				} else {
					xr.addChild("draw");
				}
			}
			
			sendGroupMessage(result);
			
			saveDBObject();
		}
	}

	@Override
	protected void playerJoined(final JID user) {
		if (players.size() == 2) {
			Collections.shuffle(players);
			
			Message msg = new Message();
			msg.addExtension("x", getXMLNS()).addChild("start").setAttribute("color", ChessColor.WHITE.name());
			sendMessage(players.get(0), msg);
			
			msg = new Message();
			msg.addExtension("x", getXMLNS()).addChild("start").setAttribute("color", ChessColor.BLACK.name());
			sendMessage(players.get(1), msg);
			
			joinable = false;
			
			saveDBObject();
		}
	}

	@Override
	protected void playerLeft(final JID user) {
		final ChessColor left = playerColor(user);
		if (left == null || chessGame.isFinished())
			return;
		
		if (players.size() == 2) {
			chessGame.setWinner(left.other());
			
			final Message msg = new Message();
			final XMLElement x = msg.addExtension("x", getXMLNS());
			x.addChild("winner").setAttribute("color", chessGame.getWinner().name());
			sendGroupMessage(msg);
			
			saveDBObject();
		}
	}
	
	@Nullable
	private final ChessColor playerColor(final JID user) {
		if (players.indexOf(user) == 0)
			return ChessColor.WHITE;
		else if (players.indexOf(user) == 1)
			return ChessColor.BLACK;
		
		return null;
	}
	
}
