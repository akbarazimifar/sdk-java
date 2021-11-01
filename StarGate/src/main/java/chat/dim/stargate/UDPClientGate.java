/* license: https://mit-license.org
 *
 *  Star Gate: Network Connection Module
 *
 *                                Written in 2021 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.stargate;

import java.net.SocketAddress;
import java.util.List;

import chat.dim.mtp.DataType;
import chat.dim.mtp.Package;
import chat.dim.mtp.PackageDocker;
import chat.dim.net.Hub;
import chat.dim.port.Docker;
import chat.dim.port.Gate;
import chat.dim.port.Ship;
import chat.dim.type.Data;
import chat.dim.udp.ClientHub;

public class UDPClientGate extends CommonGate<ClientHub> {

    public final SocketAddress remoteAddress;
    public final SocketAddress localAddress;

    public UDPClientGate(Delegate delegate, SocketAddress remote, SocketAddress local) {
        super(delegate);
        remoteAddress = remote;
        localAddress = local;
        setHub(createClientHub());
    }

    protected ClientHub createClientHub() {
        // override for user-customized hub
        return new ClientHub(this);
    }

    @Override
    protected Docker createDocker(SocketAddress remote, SocketAddress local, List<byte[]> data) {
        // TODO: check data format before create docker
        return new PackageDocker(remote, null, this) {
            @Override
            protected Hub getHub() {
                Gate gate = getGate();
                if (gate instanceof CommonGate) {
                    //noinspection rawtypes
                    return ((CommonGate) gate).getHub();
                }
                return null;
            }
        };
    }

    @Override
    public void start() {
        super.start();
        (new Thread(this)).start();
    }

    public boolean send(Package pack, int priority, Ship.Delegate delegate) {
        return send(localAddress, remoteAddress, pack, priority, delegate);
    }

    public boolean sendCommand(byte[] body, int priority, Ship.Delegate delegate) {
        Package pack = Package.create(DataType.COMMAND, null, 1, 0, -1, new Data(body));
        return send(pack, priority, delegate);
    }
    public boolean sendMessage(byte[] body, int priority, Ship.Delegate delegate) {
        Package pack = Package.create(DataType.MESSAGE, null, 1, 0, -1, new Data(body));
        return send(pack, priority, delegate);
    }
}
