/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/* Generated by WSDLToJava Compiler. */
package org.apache.cxf.performance.server;

import javax.xml.ws.Endpoint;


/**
 * This class was generated by the CXF 2.0-incubator-M1-SNAPSHOT
 * Mon Oct 09 13:22:53 CST 2006
 * Generated source version: 2.0-incubator-M1-SNAPSHOT
 * 
 */
 
public class BenchmarkServer{

    protected BenchmarkServer() throws Exception {
        System.out.println("Starting Server");
        Object implementor = new BenchmarkImpl();
        String address = "http://localhost:8080/cxf/services/Benchmark";
        Endpoint.publish(address, implementor);
    }
    
    public static void main(String args[]) throws Exception { 
        new BenchmarkServer();
        System.out.println("Server ready...");         
        Thread.sleep(Integer.MAX_VALUE); 
        System.out.println("Server exitting");
        System.exit(0);
    }
}