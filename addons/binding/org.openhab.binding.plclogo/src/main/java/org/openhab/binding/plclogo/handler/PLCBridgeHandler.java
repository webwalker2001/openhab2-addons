/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.plclogo.handler;

import static org.openhab.binding.plclogo.PLCLogoBindingConstants.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.plclogo.internal.PLCLogoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Moka7.S7;
import Moka7.S7Client;

/**
 * The {@link PLCBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public class PLCBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(PLCBridgeHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_DEVICE);

    /**
     * S7 client this bridge belongs to
     */
    private volatile PLCLogoClient client = null;
    private volatile Set<PLCBlockHandler> handlers = new HashSet<PLCBlockHandler>();

    private ScheduledFuture<?> job = null;
    private Runnable reader = new Runnable() {
        // Buffer for read operations
        final private byte[] buffer = new byte[2048];

        @Override
        public void run() {
            final Map<?, Integer> memory = LOGO_MEMORY_BLOCK.get(getLogoFamily());
            if ((memory != null) && (client != null)) {
                final Integer size = memory.get("SIZE");
                client.ReadArea(S7.S7AreaDB, 1, 0, size.intValue(), S7Client.S7WLByte, buffer);
                for (PLCBlockHandler handler : handlers) {
                    if (handler == null) {
                        continue;
                    }
                    if (handler instanceof PLCDigitalBlockHandler) {
                        final PLCDigitalBlockHandler block = (PLCDigitalBlockHandler) handler;
                        block.setData(S7.GetBitAt(buffer, block.getAddress(), block.getBit()));
                    } else if (handler instanceof PLCAnalogBlockHandler) {
                        final PLCAnalogBlockHandler block = (PLCAnalogBlockHandler) handler;
                        block.setData((short) S7.GetShortAt(buffer, block.getAddress()));
                    }
                }
            }
        }
    };

    public PLCBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} on channel {}", command, channelUID);

        final ThingUID thingUID = channelUID.getThingUID();
        if ((thingUID != null) && (thingRegistry != null) && (client != null)) {
            final PLCBlockHandler handler = (PLCBlockHandler) thingRegistry.get(thingUID).getHandler();

            int address = handler.getAddress();
            if (DIGITAL_CHANNEL_ID.equals(channelUID.getId())) {
                if ((command instanceof OnOffType) || (command instanceof OpenClosedType)) {
                    byte[] buffer = { 0 };

                    if (command instanceof OnOffType) {
                        final OnOffType state = (OnOffType) command;
                        S7.SetBitAt(buffer, 0, 0, state == OnOffType.ON);
                    } else if (command instanceof OpenClosedType) {
                        final OpenClosedType state = (OpenClosedType) command;
                        S7.SetBitAt(buffer, 0, 0, state == OpenClosedType.CLOSED);
                    }

                    address = 8 * address + handler.getBit();
                    client.WriteArea(S7.S7AreaDB, 1, address, 1, S7Client.S7WLBit, buffer);
                } else if (command instanceof RefreshType) {
                }
            } else if (ANALOG_CHANNEL_ID.equals(channelUID.getId())) {
                if (command instanceof DecimalType) {
                    byte[] buffer = { 0, 0 };

                    final DecimalType state = (DecimalType) command;
                    S7.SetShortAt(buffer, 0, (short) state.intValue());

                    client.WriteArea(S7.S7AreaDB, 1, address, 2, S7Client.S7WLByte, buffer);
                } else if (command instanceof RefreshType) {
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        logger.debug("Initialize LOGO! bridge handler.");
        final Configuration config = getConfig();

        boolean configured = config.containsKey(LOGO_HOST);
        configured = configured && config.containsKey(LOGO_LOCAL_TSAP);
        configured = configured && config.containsKey(LOGO_REMOTE_TSAP);

        if (configured) {
            if (client == null) {
                client = new PLCLogoClient();
            }
            configured = connect();
        }

        if (configured) {
            super.initialize();
            String host = null;
            Object entry = config.get(LOGO_HOST);
            if (entry instanceof String) {
                host = (String) entry;
            }

            Integer interval = Integer.valueOf(100);
            entry = getConfigParameter(LOGO_REFRESH_INTERVAL);
            if (entry instanceof String) {
                interval = Integer.decode((String) entry);
            }

            logger.info("Creating new reader job for {} with interval {} ms.", host, interval.toString());
            job = scheduler.scheduleWithFixedDelay(reader, 100, interval, TimeUnit.MILLISECONDS);
        } else {
            final String message = "Can not initialize LOGO!. Please, check parameter.";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            client = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        logger.debug("Dispose LOGO! bridge handler.");
        super.dispose();

        if (job != null) {
            job.cancel(false);
            while (!job.isDone()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                    break;
                }
            }
            job = null;
        }
        if (disconnect()) {
            client = null;
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        if (childHandler instanceof PLCBlockHandler) {
            if (!handlers.contains(childHandler)) {
                handlers.add((PLCBlockHandler) childHandler);
            } else {
                logger.info("Handler {} already registered.", childThing.getUID());
            }
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (handlers.contains(childHandler)) {
            handlers.remove(childHandler);
        }
        super.childHandlerDisposed(childHandler, childThing);
    }

    public String getLogoFamily() {
        Object family = getConfigParameter(LOGO_FAMILY);
        if (family instanceof String) {
            return (String) family;
        }
        return null;
    }

    private synchronized boolean connect() {
        Object entry = null;
        if (!client.Connected) {
            String host = null;
            entry = getConfigParameter(LOGO_HOST);
            if (entry instanceof String) {
                host = (String) entry;
            }

            Integer local = null;
            entry = getConfigParameter(LOGO_LOCAL_TSAP);
            if (entry instanceof String) {
                local = Integer.decode((String) entry);
            }

            Integer remote = null;
            entry = getConfigParameter(LOGO_REMOTE_TSAP);
            if (entry instanceof String) {
                remote = Integer.decode((String) entry);
            }

            if ((host != null) && (local != null) && (remote != null)) {
                client.Connect(host, local.intValue(), remote.intValue());
            }
        }

        return client.Connected;
    }

    private synchronized boolean disconnect() {
        boolean result = false;
        if (client != null) {
            client.Disconnect();
            result = !client.Connected;
        }
        return result;
    }

    private Object getConfigParameter(final String name) {
        Object result = null;
        Configuration config = getConfig();
        if (config.containsKey(name)) {
            result = config.get(name);
        }
        return result;
    }
}
