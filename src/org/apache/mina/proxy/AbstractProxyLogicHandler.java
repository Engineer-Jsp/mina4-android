/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.proxy;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.future.DefaultWriteFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.proxy.filter.ProxyFilter;
import org.apache.mina.proxy.filter.ProxyHandshakeIoBuffer;
import org.apache.mina.proxy.session.ProxyIoSession;
import org.apache.mina4.android.log.Mina4Log;

/**
 * AbstractProxyLogicHandler.java - Helper class to handle proxy handshaking
 * logic. Derived classes implement proxy type specific logic.
 * <p>
 * Based upon SSLHandler from mina-filter-ssl.
 * 
 * @author <a href="https://github.com/Mr-Jiang/mina4-android">mina4-android</a>
 * @since MINA 2.0.0-M3
 */
public abstract class AbstractProxyLogicHandler implements ProxyLogicHandler {

	private static final String TAG = AbstractProxyLogicHandler.class.getName();

	/**
	 * Object that contains all the proxy authentication session informations.
	 */
	private ProxyIoSession proxyIoSession;

	/**
	 * Queue of write events which occurred before the proxy handshake had
	 * completed.
	 */
	private Queue<Event> writeRequestQueue = null;

	/**
	 * Has the handshake been completed.
	 */
	private boolean handshakeComplete = false;

	/**
	 * Creates a new {@link AbstractProxyLogicHandler}.
	 * 
	 * @param proxyIoSession
	 *            {@link ProxyIoSession} in use.
	 */
	public AbstractProxyLogicHandler(ProxyIoSession proxyIoSession) {
		this.proxyIoSession = proxyIoSession;
	}

	/**
	 * @return the proxy filter {@link ProxyFilter}.
	 */
	protected ProxyFilter getProxyFilter() {
		return proxyIoSession.getProxyFilter();
	}

	/**
	 * @return the session.
	 */
	protected IoSession getSession() {
		return proxyIoSession.getSession();
	}

	/**
	 * @return the {@link ProxyIoSession} object.
	 */
	public ProxyIoSession getProxyIoSession() {
		return proxyIoSession;
	}

	/**
	 * Writes data to the proxy server.
	 * 
	 * @param nextFilter
	 *            the next filter
	 * @param data
	 *            Data buffer to be written.
	 * @return A Future for the write operation
	 */
	protected WriteFuture writeData(final NextFilter nextFilter, final IoBuffer data) {
		// write net data
		ProxyHandshakeIoBuffer writeBuffer = new ProxyHandshakeIoBuffer(data);

		Mina4Log.d(TAG, "session write: " + writeBuffer);

		WriteFuture writeFuture = new DefaultWriteFuture(getSession());
		getProxyFilter().writeData(nextFilter, getSession(), new DefaultWriteRequest(writeBuffer, writeFuture), true);

		return writeFuture;
	}

	/**
	 * @return <tt>true</tt> if handshaking is complete and data can be sent
	 *         through the proxy.
	 */
	public boolean isHandshakeComplete() {
		synchronized (this) {
			return handshakeComplete;
		}
	}

	/**
	 * Signals that the handshake has finished.
	 */
	protected final void setHandshakeComplete() {
		synchronized (this) {
			handshakeComplete = true;
		}

		ProxyIoSession proxyIoSession = getProxyIoSession();
		proxyIoSession.getConnector().fireConnected(proxyIoSession.getSession()).awaitUninterruptibly();

		Mina4Log.d(TAG, "handshake completed");

		// Connected OK
		try {
			proxyIoSession.getEventQueue().flushPendingSessionEvents();
			flushPendingWriteRequests();
		} catch (Exception ex) {
			Mina4Log.e(TAG, "Unable to flush pending write requests\n" + ex.toString());
		}
	}

	/**
	 * Send any write requests which were queued whilst waiting for handshaking
	 * to complete.
	 * 
	 * @throws Exception
	 *             If we can't flush the pending write requests
	 */
	protected synchronized void flushPendingWriteRequests() throws Exception {
		Mina4Log.d(TAG, "flushPendingWriteRequests()");

		if (writeRequestQueue == null) {
			return;
		}

		Event scheduledWrite;
		while ((scheduledWrite = writeRequestQueue.poll()) != null) {
			Mina4Log.d(TAG, "Flushing buffered write request: " + scheduledWrite.data);

			getProxyFilter().filterWrite(scheduledWrite.nextFilter, getSession(), (WriteRequest) scheduledWrite.data);
		}

		// Free queue
		writeRequestQueue = null;
	}

	/**
	 * Enqueue a message to be written once handshaking is complete.
	 */
	public synchronized void enqueueWriteRequest(final NextFilter nextFilter, final WriteRequest writeRequest) {
		if (writeRequestQueue == null) {
			writeRequestQueue = new LinkedList<Event>();
		}

		writeRequestQueue.offer(new Event(nextFilter, writeRequest));
	}

	/**
	 * Closes the session.
	 * 
	 * @param message
	 *            the error message
	 * @param t
	 *            the exception which caused the session closing
	 */
	protected void closeSession(final String message, final Throwable t) {
		if (t != null) {
			Mina4Log.e(TAG, message + "\n" + t.toString());
			proxyIoSession.setAuthenticationFailed(true);
		} else {
			Mina4Log.e(TAG, message);
		}

		getSession().closeNow();
	}

	/**
	 * Closes the session.
	 * 
	 * @param message
	 *            the error message
	 */
	protected void closeSession(final String message) {
		closeSession(message, null);
	}

	/**
	 * Event wrapper class for enqueued events.
	 */
	private final static class Event {
		private final NextFilter nextFilter;

		private final Object data;

		Event(final NextFilter nextFilter, final Object data) {
			this.nextFilter = nextFilter;
			this.data = data;
		}
	}
}