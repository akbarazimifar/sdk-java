/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
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
package chat.dim.cpu;

import chat.dim.protocol.Content;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.ReliableMessage;

public class ForwardContentProcessor extends ContentProcessor {

    public ForwardContentProcessor() {
        super();
    }

    @Override
    public Content process(Content content, ReliableMessage rMsg) {
        assert content instanceof ForwardContent : "forward content error: " + content;
        ForwardContent forward = (ForwardContent) content;
        ReliableMessage secret = forward.getMessage();
        // call messenger to process it
        secret = getMessenger().getMessageProcessor().process(secret);
        // check response
        if (secret != null) {
            // Over The Top
            return new ForwardContent(secret);
        }/* else {
            Object receiver = forward.forwardMessage.getReceiver();
            String text = String.format("Message forwarded: %s", receiver);
            return new ReceiptCommand(text);
        }*/

        // NOTICE: decrypt failed, not for you?
        //         it means you are asked to re-pack and forward this message
        return null;
    }
}
