package com.ls.agent.web.client;

import java.io.IOException;
import java.io.OutputStream;

public interface GatewayClient {

    void streamTest(OutputStream output) throws IOException;

    void chatStream(InternalChatStreamRequest request, OutputStream output) throws IOException;
}
