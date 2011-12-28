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

import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import es.udc.pfc.gamelib.board.Position;
import es.udc.pfc.gamelib.chess.ChessColor;
import es.udc.pfc.gamelib.chess.ChessGame;
import es.udc.pfc.gamelib.chess.ChessMovement;

public abstract class ChessRoom extends AbstractRoom {
	
	private final ChessGame chessGame;
	
	private boolean joinable;

	protected ChessRoom(final GameComponent component, final JID roomJID, final ChessGame chessGame) {
		super(component, roomJID);
		this.chessGame = checkNotNull(chessGame);
		joinable = true;
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
	protected final void commandReceived(final JID user, final Element x) throws Exception {
		if (players.size() != 2) 
			throw new Exception("not-started");
		
		if (x.element("move") != null) {
			final Element xmove = x.element("move");
			
			if (playerColor(user) != chessGame.getCurrentTurn())
				throw new Exception("invalid-turn");

			final Position from = Position.fromString(xmove.attributeValue("from"));
			final Position to = Position.fromString(xmove.attributeValue("to"));

			if (from == null || to == null)
				throw new Exception("invalid-position");

			final ChessMovement move = chessGame.movePiece(from, to);
			if (move == null)
				throw new Exception("invalid-movement");
			
			final Message result = new Message();
			final Element xr = result.addChildElement("x", getXMLNS());
			final Element xrmove = xr.addElement("move");
			xrmove.addAttribute("from", move.getFrom().toString());
			xrmove.addAttribute("to", move.getTo().toString());
			
			if (chessGame.isFinished()) {
				if (chessGame.getWinner() != null) {
					xr.addElement("winner").addAttribute("color", chessGame.getWinner().name());
				} else {
					xr.addElement("draw");
				}
			}
			
			sendGroupMessage(result);
		}
	}

	@Override
	protected void playerJoined(final JID user) {
		if (players.size() == 2) {
			Collections.shuffle(players);
			
			Message msg = new Message();
			msg.addChildElement("x", getXMLNS()).addElement("start").addAttribute("color", ChessColor.WHITE.name());
			sendMessage(players.get(0), msg);
			
			msg = new Message();
			msg.addChildElement("x", getXMLNS()).addElement("start").addAttribute("color", ChessColor.BLACK.name());
			sendMessage(players.get(1), msg);
			
			joinable = false;
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
			final Element x = msg.addChildElement("x", getXMLNS());
			x.addElement("winner").addAttribute("color", chessGame.getWinner().name());
			sendGroupMessage(msg);
		}
	}
	
	private final ChessColor playerColor(final JID user) {
		if (players.indexOf(user) == 0)
			return ChessColor.WHITE;
		else if (players.indexOf(user) == 1)
			return ChessColor.BLACK;
		
		return null;
	}
	
}
