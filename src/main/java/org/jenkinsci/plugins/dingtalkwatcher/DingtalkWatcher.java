package org.jenkinsci.plugins.dingtalkwatcher;

import com.arronlong.httpclientutil.HttpClientUtil;
import com.arronlong.httpclientutil.common.HttpConfig;
import com.arronlong.httpclientutil.exception.HttpProcessException;
import hudson.Plugin;
import hudson.model.User;
import hudson.plugins.jobConfigHistory.JobConfigHistory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.jenkinsci.plugins.dingtalkwatcher.jobConfigHistory.ConfigHistory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.net.ssl.SSLContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class DingtalkWatcher {
    private static final Logger LOGGER = Logger.getLogger(DingtalkWatcher.class.getName());

    private final @Nonnull
    Jenkins jenkins;
    private final @Nonnull
    ConfigHistory configHistory;

    public DingtalkWatcher(final @Nonnull Jenkins jenkins) {
        this.jenkins = jenkins;
        this.configHistory = new ConfigHistory((JobConfigHistory) plugin("jobConfigHistory"));
    }

    @Nonnull
    User getDefaultInitiator() {
        final User current = User.current();
        return current != null ? current : User.getUnknown();
    }

    @CheckForNull
    Plugin plugin(final String plugin) {
        return jenkins.getPlugin(plugin);
    }

    @Nonnull
    URL absoluteUrl(final @Nonnull String url) {
        try {
            return new URL(jenkins.getRootUrl() + url);
        } catch (MalformedURLException ex) {
            throw new AssertionError(ex);
        }
    }

    @Nonnull
    ConfigHistory configHistory() {
        return configHistory;
    }


    public String send(final DingtalkWatcherNotification notification) throws MessagingException, AddressException {
        //if (!notification.shouldNotify()) return null; 这里判断 收件人 是否是空了，是空就不通知了。
        String webhookurl = notification.getWebhookurl();

        String[] urls;
        if (webhookurl.contains(",")) {
            urls = webhookurl.split(",");
        } else {
            urls = new String[]{webhookurl};
        }
        if (urls.length == 0) {
            LOGGER.warning("webhookurl is null");
            return null;
        }

        String data = toJSONString(notification);

        LOGGER.info("will send msg: " + data);
        for (String u : urls) {
            try {
                String msg = push(u, data);
                LOGGER.info("send msg result" + msg);
            } catch (HttpProcessException | KeyManagementException | NoSuchAlgorithmException e) {
                LOGGER.info("send msg result" + e.getMessage());
                e.printStackTrace();
            }
        }
        return "";
    }

    private String toJSONString(final DingtalkWatcherNotification notification) {
        //组装内容
        StringBuilder content = new StringBuilder();
        StringBuilder subject = new StringBuilder();
        subject.append(notification.getMailSubject());

        content.append("# " + notification.getMailSubject() + "\n\n");
        content.append(notification.getMailBody());

        Map mdMap = new HashMap<String, Object>();
        mdMap.put("title", subject.toString());
        mdMap.put("text", content.toString());



        Map data = new HashMap<String, Object>();
        data.put("msgtype", "markdown");
        data.put("markdown", mdMap);

        String req = JSONObject.fromObject(data).toString();
        return req;
    }

    private static String push(String url, String data) throws HttpProcessException, KeyManagementException, NoSuchAlgorithmException {
        HttpConfig httpConfig;
        HttpClient httpClient;
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (url.startsWith("https")) {
            SSLContext sslContext = SSLContexts.custom().build();
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"},
                    null,
                    NoopHostnameVerifier.INSTANCE
            );
            httpClientBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
        }

        httpClient = httpClientBuilder.build();
        //普通请求
        httpConfig = HttpConfig.custom().client(httpClient).url(url).json(data).encoding("utf-8");

        String result = HttpClientUtil.post(httpConfig);
        return result;
    }

}
