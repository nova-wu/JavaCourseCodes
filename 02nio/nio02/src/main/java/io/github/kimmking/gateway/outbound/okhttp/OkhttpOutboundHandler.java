package io.github.kimmking.gateway.outbound.okhttp;

import com.sun.tools.javac.code.Scope;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Iterator;

import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class OkhttpOutboundHandler {

    private String backendUrl;

    public OkhttpOutboundHandler(String backendUrl){
       this.backendUrl=backendUrl.endsWith("/")?backendUrl.substring(0,backendUrl.length()-1):backendUrl;
    }

    public void handle(final FullHttpRequest fullRequest, final ChannelHandlerContext ctx) {
        final String url = this.backendUrl + fullRequest.uri();
        // 发请求到 url ，并将返回内容写入ctx
        final OkHttpClient httpClient = new OkHttpClient();

             //netty header --> okhttp header
        Headers.Builder headersBuilder = new Headers.Builder();
        fullRequest.headers().forEach(stringStringEntry -> {
            headersBuilder.add(stringStringEntry.getKey(), stringStringEntry.getValue())
            ;
        });
        Headers headers = headersBuilder.build();

        Request request = new Request.Builder()
                .url(url)
                .headers(headers)
                .build();
        FullHttpResponse response = null;
        try (Response endponitResponse = httpClient.newCall(request).execute()) {
            if (!endponitResponse.isSuccessful()) throw new IOException("Unexpected code " + endponitResponse);
            Headers responseHeaders = endponitResponse.headers();
            for (int i = 0; i < responseHeaders.size(); i++) {
                System.out.println(responseHeaders.name(i) + ": " + responseHeaders.value(i));
            }
//            System.out.println(endponitResponse.body().string());
            response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(endponitResponse.body().bytes()));
            response.headers().set("Content-Type", "application/json");
            response.headers().setInt("Content-Length", Integer.parseInt(endponitResponse.headers().get("Content-Length")));
        } catch (IOException e){
            e.printStackTrace();
            response = new DefaultFullHttpResponse(HTTP_1_1, NO_CONTENT);
            e.printStackTrace();
            ctx.close();
        } finally {

            if (fullRequest != null) {
                if (!HttpUtil.isKeepAlive(fullRequest)) {
                    ctx.write(response).addListener(ChannelFutureListener.CLOSE);
                } else {
                    //response.headers().set(CONNECTION, KEEP_ALIVE);
                    ctx.write(response);
                }
            }
            ctx.flush();
            //ctx.close();
        }
    }
}
