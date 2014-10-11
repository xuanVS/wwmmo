package au.com.codeka.warworlds.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Random;
import java.util.regex.Matcher;

import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import au.com.codeka.common.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.squareup.wire.Message;
import com.squareup.wire.Wire;
import com.squareup.wire.WireTypeAdapterFactory;

import au.com.codeka.warworlds.server.ctrl.SessionController;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.handlers.admin.AdminGenericHandler;


/**
 * This is the base class for the game's request handlers. It handles some common tasks such as
 * extracting protocol buffers from the request body, and so on.
 */
public class RequestHandler {
    private final Log log = new Log("RequestHandler");
    private HttpServletRequest mRequest;
    private HttpServletResponse mResponse;
    private Matcher mRouteMatcher;
    private Session mSession;
    private String mExtraOption;

    protected static Wire wire = new Wire();

    protected String getUrlParameter(String name) {
        return mRouteMatcher.group(name);
    }

    protected String getRealm() {
        return getUrlParameter("realm");
    }

    protected String getExtraOption() {
        return mExtraOption;
    }

    public void handle(Matcher matcher, String extraOption, HttpServletRequest request,
                       HttpServletResponse response) {
        mRequest = request;
        mResponse = response;
        mRouteMatcher = matcher;
        mExtraOption = extraOption;

        RequestContext.i.setContext(request);

        // start off with status 200, but the handler might change it
        mResponse.setStatus(200);

        RequestException lastException = null;
        for (int retries = 0; retries < 10; retries++) {
            try {
                onBeforeHandle();
                if (request.getMethod().equals("GET")) {
                    get();
                } else if (request.getMethod().equals("POST")) {
                    post();
                } else if (request.getMethod().equals("PUT")) {
                    put();
                } else if (request.getMethod().equals("DELETE")) {
                    delete();
                } else {
                    throw new RequestException(501);
                }

                return; // break out of the retry loop
            } catch(RequestException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SQLException && DB.isRetryable((SQLException) cause)
                        && supportsRetryOnDeadlock()) {
                    try {
                        Thread.sleep(50 + new Random().nextInt(100));
                    } catch (InterruptedException e1) {
                    }
                    log.warning("Retrying deadlock.", e);
                    lastException = e;
                    continue;
                }
                if (e.getHttpErrorCode() < 500) {
                    log.warning("Unhandled error in URL: "+request.getRequestURI(), e);
                } else {
                    log.error("Unhandled error in URL: "+request.getRequestURI(), e);
                }
                e.populate(mResponse);
                setResponseBody(e.getGenericError());
                return;
            } catch(Throwable e) {
                log.error("Unhandled error!", e);
                mResponse.setStatus(500);
                return;
            }
        }

        // if we get here, it's because we exceeded the number of retries.
        if (lastException != null) {
            log.error("Too many retries: "+request.getRequestURI(), lastException);
            lastException.populate(mResponse);
            handleException(lastException);
        }
    }

    protected void handleException(RequestException e) {
        setResponseBody(e.getGenericError());
    }

    /**
     * This is called before the get(), put(), etc methods but after the request
     * is set up, ready to go.
     */
    protected void onBeforeHandle() throws RequestException {
    }

    protected void get() throws RequestException {
        throw new RequestException(501);
    }

    protected void put() throws RequestException {
        throw new RequestException(501);
    }

    protected void post() throws RequestException {
        throw new RequestException(501);
    }

    protected void delete() throws RequestException {
        throw new RequestException(501);
    }

    /**
     * You can override this in subclass to indicate that the request supports automatic
     * retry on deadlock.
     */
    protected boolean supportsRetryOnDeadlock() {
        return false;
    }

    protected void setResponseText(String text) {
        mResponse.setContentType("text/plain");
        mResponse.setCharacterEncoding("utf-8");
        try {
            mResponse.getWriter().write(text);
        } catch (IOException e) {
        }
    }

    protected void setResponseJson(JsonObject json) {
        mResponse.setContentType("application/json");
        mResponse.setCharacterEncoding("utf-8");
        try {
            mResponse.getWriter().write(json.toString());
        } catch (IOException e) {
        }
    }

    protected void setResponseBody(Message pb) {
        if (pb == null) {
            return;
        }

        if (mRequest.getHeader("Accept") != null) {
            for (String acceptValue : mRequest.getHeader("Accept").split(",")) {
                if (acceptValue.startsWith("text/")) {
                    setResponseBodyText(pb);
                    return;
                } else if (acceptValue.startsWith("application/json")) {
                    setResponseBodyJson(pb);
                    return;
                }
            }
        }

        mResponse.setContentType("application/x-protobuf");
        mResponse.setHeader("Content-Type", "application/x-protobuf");
        try {
            // TODO: is this the most efficient way?
            mResponse.getOutputStream().write(pb.toByteArray());
        } catch (IOException e) {
        }
    }

    private void setResponseBodyText(Message pb) {
        mResponse.setContentType("text/plain");
        mResponse.setCharacterEncoding("utf-8");
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapterFactory(new WireTypeAdapterFactory(wire))
                    .disableHtmlEscaping()
                    .setPrettyPrinting()
                    .create();
            mResponse.getWriter().write(gson.toJson(pb));
        } catch (IOException e) {
        }
    }

    private void setResponseBodyJson(Message pb) {
        mResponse.setContentType("application/json");
        mResponse.setCharacterEncoding("utf-8");
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapterFactory(new WireTypeAdapterFactory(wire))
                    .disableHtmlEscaping()
                    .create();
            mResponse.getWriter().write(gson.toJson(pb));
        } catch (IOException e) {
        }
    }

    protected void redirect(String url) {
        mResponse.setStatus(302);
        mResponse.addHeader("Location", url);
    }

    protected HttpServletRequest getRequest() {
        return mRequest;
    }
    protected HttpServletResponse getResponse() {
        return mResponse;
    }

    protected String getRequestUrl() {
        URI requestURI = null;
        try {
            requestURI = new URI(mRequest.getRequestURL().toString());
        } catch (URISyntaxException e) {
            return null; // should never happen!
        }

        // TODO(deanh): is hard-coding the https part for game.war-worlds.com the best way? no...
        if (requestURI.getHost().equals("game.war-worlds.com")) {
            return "https://game.war-worlds.com"+requestURI.getPath();
        } else {
            return requestURI.toString();
        }
    }

    protected Session getSession() throws RequestException {
        if (mSession == null) {
            String impersonate = getRequest().getParameter("on_behalf_of");

            if (mRequest.getCookies() == null) {
                throw new RequestException(403);
            }

            String sessionCookieValue = "";
            for (Cookie cookie : mRequest.getCookies()) {
                if (cookie.getName().equals("SESSION")) {
                    sessionCookieValue = cookie.getValue();
                    mSession = new SessionController().getSession(sessionCookieValue, impersonate);
                }
            }

            if (mSession == null) {
                throw new RequestException(403);
            }
        }

        return mSession;
    }

    protected Session getSessionNoError() {
        try {
            return getSession();
        } catch(RequestException e) {
            return null;
        }
    }

    protected boolean isAdmin() throws RequestException {
        Session s = getSessionNoError();
        return (s != null && s.isAdmin());
    }

    protected <T extends Message> T getRequestBody(Class<T> messageClass) {
        if (mRequest.getHeader("Content-Type").equals("application/json")) {
            return getRequestBodyJson(messageClass);
        }

        T result = null;
        ServletInputStream ins = null;

        try {
            ins = mRequest.getInputStream();
            return wire.parseFrom(ins, messageClass);
        } catch (Exception e) {
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException e) {
                }
            }
        }

        return result;
    }

    /**
     * Gets a file representing the "base" path where everything in installed into (e.g. the "data"
     * directory is relative to this path.
     */
    protected static File getBasePath() {
        String path = System.getProperty("au.com.codeka.warworlds.server.basePath");
        if (path == null) {
            URL url = AdminGenericHandler.class.getClassLoader().getResource("");
            if (url == null) {
                try {
                    URI uri = AdminGenericHandler.class.getProtectionDomain().getCodeSource()
                            .getLocation().toURI();
                    if (uri != null) { // dafuk is dis?
                        url = new URL(uri.getPath());
                    }
                } catch (Exception e) {}
            }
            if (url != null) {
                path = url.getPath();
            }
        }
        if (path == null) {
            return new File(".");
        }
        return new File(path+"../").getAbsoluteFile();
    }

    private <T extends Message> T getRequestBodyJson(Class<T> messageClass) {
        ServletInputStream ins = null;
        try {
            ins = mRequest.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(ins));

            Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new WireTypeAdapterFactory(wire))
                .create();

            return gson.fromJson(reader, messageClass);
        } catch (Exception e) {
            return null;
        } finally {
            if (ins != null) {
                try { ins.close(); } catch (IOException e) {}
            }
        }
    }
}
