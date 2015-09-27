/**
 * Copyright (C) 2013 by Raphael Michel under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software 
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.apis;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.geeksfactory.opacclient.NotReachableException;
import de.geeksfactory.opacclient.SSLSecurityException;
import de.geeksfactory.opacclient.i18n.DummyStringProvider;
import de.geeksfactory.opacclient.i18n.StringProvider;
import de.geeksfactory.opacclient.networking.HTTPClient;
import de.geeksfactory.opacclient.networking.HttpUtils;
import de.geeksfactory.opacclient.objects.CoverHolder;
import de.geeksfactory.opacclient.objects.Library;
import de.geeksfactory.opacclient.objects.SearchRequestResult;
import de.geeksfactory.opacclient.searchfields.SearchQuery;

/**
 * Abstract Base class for OpacApi implementations providing some helper methods for HTTP
 */
public abstract class BaseApi implements OpacApi {

    protected HttpClient http_client;
    protected Library library;
    protected StringProvider stringProvider;
    protected Set<String> supportedLanguages;
    protected boolean initialised;

    /**
     * Cleans the parameters of a URL by parsing it manually and reformatting it using {@link
     * URLEncodedUtils#format(java.util.List, String)}
     *
     * @param myURL the URL to clean
     * @return cleaned URL
     */
    public static String cleanUrl(String myURL) {
        String[] parts = myURL.split("\\?");
        String url = parts[0];
        try {
            if (parts.length > 1) {
                url += "?";
                List<NameValuePair> params = new ArrayList<>();
                String[] pairs = parts[1].split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=");
                    if (kv.length > 1) {
                        params.add(new BasicNameValuePair(URLDecoder.decode(
                                kv[0], "UTF-8"), URLDecoder.decode(kv[1],
                                "UTF-8")));
                    } else {
                        params.add(new BasicNameValuePair(URLDecoder.decode(
                                kv[0], "UTF-8"), ""));
                    }
                }
                url += URLEncodedUtils.format(params, "UTF-8");
            }
            return url;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return myURL;
        }
    }

    /**
     * Reads content from an InputStream into a string
     *
     * @param is       InputStream to read from
     * @param encoding the encoding to use
     * @return String content of the InputStream
     */
    protected static String convertStreamToString(InputStream is,
            String encoding) throws IOException {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(is, encoding));
        } catch (UnsupportedEncodingException e1) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * Reads content from an InputStream into a string, using the default {@code ISO-8859-1} encoding
     *
     * @param is InputStream to read from
     * @return String content of the InputStream
     */
    protected static String convertStreamToString(InputStream is)
            throws IOException {
        return convertStreamToString(is, "ISO-8859-1");
    }

    /**
     * Converts a {@link List} of {@link SearchQuery}s to {@link Map} of their keys and values. Can
     * be used to convert old implementations using {@code search(Map<String, String>)} to the new
     * SearchField API
     *
     * @param queryList List of search queries
     * @return Map of their keys and values
     */
    protected static Map<String, String> searchQueryListToMap(
            List<SearchQuery> queryList) {
        Map<String, String> queryMap = new HashMap<>();
        for (SearchQuery query : queryList) {
            queryMap.put(query.getKey(), query.getValue());
        }
        return queryMap;
    }

    /*
     * Gets all values of all query parameters in an URL.
     */
    public static Map<String, List<String>> getQueryParams(String url) {
        try {
            Map<String, List<String>> params = new HashMap<>();
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1) {
                String query = urlParts[1];
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8");
                    }

                    List<String> values = params.get(key);
                    if (values == null) {
                        values = new ArrayList<>();
                        params.put(key, values);
                    }
                    values.add(value);
                }
            }

            return params;
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
    }

    /*
     * Gets the value for every query parameter in the URL. If a parameter name
     * occurs twice or more, only the first occurrence is interpreted by this
     * method
     */
    public static Map<String, String> getQueryParamsFirst(String url) {
        try {
            Map<String, String> params = new HashMap<>();
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1) {
                String query = urlParts[1];
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8");
                    }

                    String values = params.get(key);
                    if (values == null) {
                        params.put(key, value);
                    }
                }
            }

            return params;
        } catch (UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Initializes HTTP client and String Provider
     */
    @Override
    public void init(Library library) {
        http_client = HTTPClient.getNewHttpClient(library.getData().optBoolean("customssl", false),
                library.getData().optBoolean("disguise", false));
        this.library = library;
        stringProvider = new DummyStringProvider();
    }

    public void start() throws IOException {
        supportedLanguages = getSupportedLanguages();
        initialised = true;
    }

    /**
     * Perform a HTTP GET request to a given URL
     *
     * @param url           URL to fetch
     * @param encoding      Expected encoding of the response body
     * @param ignore_errors If true, status codes above 400 do not raise an exception
     * @param cookieStore   If set, the given cookieStore is used instead of the built-in one.
     * @return Answer content
     * @throws NotReachableException Thrown when server returns a HTTP status code greater or equal
     *                               than 400.
     */
    public String httpGet(String url, String encoding, boolean ignore_errors,
            CookieStore cookieStore) throws
            IOException {

        HttpGet httpget = new HttpGet(cleanUrl(url));
        HttpResponse response;
        String html;

        try {
            if (cookieStore != null) {
                // Create local HTTP context
                HttpContext localContext = new BasicHttpContext();
                // Bind custom cookie store to the local context
                localContext.setAttribute(ClientContext.COOKIE_STORE,
                        cookieStore);

                response = http_client.execute(httpget, localContext);
            } else {
                response = http_client.execute(httpget);
            }

            if (!ignore_errors && response.getStatusLine().getStatusCode() >= 400) {
                HttpUtils.consume(response.getEntity());
                throw new NotReachableException(response.getStatusLine().getReasonPhrase());
            }

            html = convertStreamToString(response.getEntity().getContent(),
                    encoding);
            HttpUtils.consume(response.getEntity());
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            e.printStackTrace();
            throw new SSLSecurityException(e.getMessage());
        } catch (javax.net.ssl.SSLException e) {
            // Can be "Not trusted server certificate" or can be a
            // aborted/interrupted handshake/connection
            if (e.getMessage().contains("timed out")
                    || e.getMessage().contains("reset by")) {
                e.printStackTrace();
                throw new NotReachableException(e.getMessage());
            } else {
                e.printStackTrace();
                throw new SSLSecurityException(e.getMessage());
            }
        } catch (InterruptedIOException e) {
            e.printStackTrace();
            throw new NotReachableException(e.getMessage());
        } catch (IOException e) {
            if (e.getMessage() != null
                    && e.getMessage().contains("Request aborted")) {
                e.printStackTrace();
                throw new NotReachableException(e.getMessage());
            } else {
                throw e;
            }
        }
        return html;
    }

    public String httpGet(String url, String encoding, boolean ignore_errors)
            throws IOException {
        return httpGet(url, encoding, ignore_errors, null);
    }

    public String httpGet(String url, String encoding)
            throws IOException {
        return httpGet(url, encoding, false, null);
    }

    @Deprecated
    public String httpGet(String url) throws
            IOException {
        return httpGet(url, getDefaultEncoding(), false, null);
    }

    /**
     * Downloads a cover to a CoverHolder. You only need to use this if the covers are only
     * available with e.g. Session cookies. Otherwise, it is sufficient to specify the URL of the
     * cover.
     *
     * @param item CoverHolder to download the cover for
     */
    public void downloadCover(CoverHolder item) {
        if (item.getCover() == null) {
            return;
        }
        HttpGet httpget = new HttpGet(cleanUrl(item.getCover()));
        HttpResponse response;

        try {
            response = http_client.execute(httpget);

            if (response.getStatusLine().getStatusCode() >= 400) {
                return;
            }
            HttpEntity entity = response.getEntity();
            byte[] bytes = EntityUtils.toByteArray(entity);

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0,
                    bytes.length);
            item.setCoverBitmap(bitmap);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Perform a HTTP POST request to a given URL
     *
     * @param url           URL to fetch
     * @param data          POST data to send
     * @param encoding      Expected encoding of the response body
     * @param ignore_errors If true, status codes above 400 do not raise an exception
     * @param cookieStore   If set, the given cookieStore is used instead of the built-in one.
     * @return Answer content
     * @throws NotReachableException Thrown when server returns a HTTP status code greater or equal
     *                               than 400.
     */
    public String httpPost(String url, HttpEntity data,
                           String encoding, boolean ignore_errors, CookieStore cookieStore)
            throws IOException {
        HttpPost httppost = new HttpPost(cleanUrl(url));
        httppost.setEntity(data);

        HttpResponse response;
        String html;
        try {
            if (cookieStore != null) {
                // Create local HTTP context
                HttpContext localContext = new BasicHttpContext();
                // Bind custom cookie store to the local context
                localContext.setAttribute(ClientContext.COOKIE_STORE,
                        cookieStore);

                response = http_client.execute(httppost, localContext);
            } else {
                response = http_client.execute(httppost);
            }

            if (!ignore_errors && response.getStatusLine().getStatusCode() >= 400) {
                throw new NotReachableException(response.getStatusLine().getReasonPhrase());
            }
            html = convertStreamToString(response.getEntity().getContent(),
                    encoding);
            HttpUtils.consume(response.getEntity());
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            e.printStackTrace();
            throw new SSLSecurityException(e.getMessage());
        } catch (javax.net.ssl.SSLException e) {
            // Can be "Not trusted server certificate" or can be a
            // aborted/interrupted handshake/connection
            if (e.getMessage().contains("timed out")
                    || e.getMessage().contains("reset by")) {
                e.printStackTrace();
                throw new NotReachableException(e.getMessage());
            } else {
                e.printStackTrace();
                throw new SSLSecurityException(e.getMessage());
            }
        } catch (InterruptedIOException e) {
            e.printStackTrace();
            throw new NotReachableException(e.getMessage());
        } catch (IOException e) {
            if (e.getMessage() != null
                    && e.getMessage().contains("Request aborted")) {
                e.printStackTrace();
                throw new NotReachableException(e.getMessage());
            } else {
                throw e;
            }
        }
        return html;
    }

    public String httpPost(String url, HttpEntity data,
                           String encoding, boolean ignore_errors)
            throws IOException {
        return httpPost(url, data, encoding, ignore_errors, null);
    }

    public String httpPost(String url, HttpEntity data,
                           String encoding) throws IOException {
        return httpPost(url, data, encoding, false, null);
    }

    @Deprecated
    public String httpPost(String url, HttpEntity data)
            throws IOException {
        return httpPost(url, data, getDefaultEncoding(), false, null);
    }

    protected String getDefaultEncoding() {
        return "ISO-8859-1";
    }

    @Override
    public boolean shouldUseMeaningDetector() {
        return true;
    }

    @Override
    public SearchRequestResult volumeSearch(Map<String, String> query)
            throws IOException, OpacErrorException {
        return null;
    }

    @Override
    public void setStringProvider(StringProvider stringProvider) {
        this.stringProvider = stringProvider;
    }

    public static String buildHttpGetParams(List<NameValuePair> params)
            throws UnsupportedEncodingException {
        try {
            return new URIBuilder().addParameters(params).build().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
