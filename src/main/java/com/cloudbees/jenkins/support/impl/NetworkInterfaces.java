/*
 * The MIT License
 * 
 * Copyright (c) 2015 schristou88
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.security.Permission;
import hudson.util.HexBinaryConverter;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author schristou88
 */
@Extension
public class NetworkInterfaces extends Component {
    private final WeakHashMap<Node, String> networkInterfaceCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Networking Interface";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(
                new Content("nodes/master/networkInterface.md") {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        os.write(getNetworkInterface(Jenkins.getInstance()).getBytes());
                    }
                }
        );

        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(
                    new Content("nodes/slave/" + node.getNodeName() + "/networkInterface.md") {
                        @Override
                        public void writeTo(OutputStream os) throws IOException {
                            os.write(getNetworkInterface(node).getBytes());
                        }
                    }
            );
        }
    }

    public String getNetworkInterface(Node node) throws IOException {
        return AsyncResultCache.get(node,
                networkInterfaceCache,
                new GetNetworkInterfaces(),
                "network interfaces",
                "N/A: No connection to node, or no cache.");
    }

    private static final class GetNetworkInterfaces implements Callable<String, RuntimeException> {
        public String call() {
            StringWriter bos = new StringWriter();
            PrintWriter pw = new PrintWriter(bos);

            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    pw.println("-----------");

                    NetworkInterface ni = networkInterfaces.nextElement();
                    pw.println(" * Name " + ni.getDisplayName());

                    byte[] hardwareAddress = ni.getHardwareAddress();

                    // Do not have permissions or address does not exist
                    if (hardwareAddress == null || hardwareAddress.length == 0)
                        continue;

                    pw.println(" ** Hardware Address - " + Util.toHexString(hardwareAddress));
                    pw.println(" ** Index - " + ni.getIndex());
                    pw.println(" ** Inet Address - " + ni.getInetAddresses());
                    pw.println(" ** MTU - " + ni.getMTU());
                    pw.println(" ** Is Up - " + ni.isUp());
                    pw.println(" ** Is Virtual - " + ni.isVirtual());
                    pw.println(" ** Is Loopback - " + ni.isLoopback());
                    pw.println(" ** Is Point to Point - " + ni.isPointToPoint());
                    pw.println(" ** Supports multicast - " + ni.supportsMulticast());

                    if (ni.getParent() != null) {
                        pw.println(" ** Child of - " + ni.getParent().getDisplayName());
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace(pw);
            } finally {
                pw.flush();
            }

            return bos.toString();
        }
    }
}
