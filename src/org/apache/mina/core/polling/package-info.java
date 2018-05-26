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

/**
 * Base class for implementing transport based on active polling strategies like NIO select call, 
 * or any API based on I/O polling system calls (epoll, poll, select, kqueue, etc). Know 
 * implementations are org.apache.mina.transport.socket.nio and org.apache.mina.transport.socket.apr.
 * 
 * @author <a href="https://github.com/Mr-Jiang/mina4-android">mina4-android</a>
 */
package org.apache.mina.core.polling;