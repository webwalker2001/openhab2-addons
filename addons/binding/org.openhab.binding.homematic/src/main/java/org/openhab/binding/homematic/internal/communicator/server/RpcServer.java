/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.homematic.internal.communicator.server;

import java.io.IOException;
import java.util.List;

/**
 * RPC server interface.
 *
 * @author Gerhard Riegler - Initial contribution
 */
public interface RpcServer {

    /**
     * Starts the rpc server.
     */
    public void start() throws IOException;

    /**
     * Stops the rpc server.
     */
    public void shutdown();

    /**
     * Creates a BINRPC message with the supported method names.
     */
    public List<String> getListMethods();

    /**
     * Populates the extracted event to the listener.
     */
    public void handleEvent(Object[] message) throws IOException;

    /**
     * Calls the listener when a devices has been detected.
     */
    public void handleNewDevice(Object[] message) throws IOException;

    /**
     * Calls the listener when devices has been deleted.
     */
    public void handleDeleteDevice(Object[] message) throws IOException;

}
