/**
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.ndp.transport.http.msgprocess;

import java.io.IOException;
import java.util.Map;

import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.ndp.messaging.v1.MessageHandler;
import org.neo4j.ndp.runtime.Session;
import org.neo4j.ndp.runtime.StatementMetadata;
import org.neo4j.stream.RecordStream;

/** Bridges the gap between incoming deserialized messages, the user environment and back. */
class TransportBridge extends MessageHandler.Adapter<RuntimeException>
{
    // Note that these callbacks can be used for multiple in-flight requests simultaneously, you cannot reset them
    // while there are in-flight requests.
    private final org.neo4j.ndp.transport.http.msgprocess.MessageProcessingCallback<StatementMetadata> runCallback;
    private final org.neo4j.ndp.transport.http.msgprocess.MessageProcessingCallback<RecordStream> resultStreamCallback;
    private final org.neo4j.ndp.transport.http.msgprocess.MessageProcessingCallback<Void> simpleCallback;

    private Session session;

    public TransportBridge( StringLogger log )
    {
        this.resultStreamCallback = new ResultStreamCallback( log );
        this.simpleCallback = new org.neo4j.ndp.transport.http.msgprocess.MessageProcessingCallback<>(log);
        this.runCallback = new org.neo4j.ndp.transport.http.msgprocess.RunCallback( log );
    }

    /**
     * Reset this bridge to be used for a different session. This method CANNOT be called while there are in-flight
     * requests for this bridge running, doing so will cause protocol errors.
     */
    public TransportBridge reset( Session session, MessageHandler<IOException> output,
                                  Runnable onEachCompletedRequest )
    {
        this.session = session;
        this.simpleCallback.reset( output, onEachCompletedRequest );
        this.resultStreamCallback.reset( output, onEachCompletedRequest );
        this.runCallback.reset( output, onEachCompletedRequest );
        return this;
    }

    @Override
    public void handleRunMessage( String statement, Map<String,Object> params )
    {
        session.run( statement, params, null, runCallback );
    }

    @Override
    public void handlePullAllMessage()
    {
        session.pullAll( null, resultStreamCallback );
    }

    @Override
    public void handleDiscardAllMessage()
    {
        session.discardAll( null, simpleCallback );
    }

    @Override
    public void handleAckFailureMessage() throws RuntimeException
    {
        session.acknowledgeFailure( null, simpleCallback );
    }
}
