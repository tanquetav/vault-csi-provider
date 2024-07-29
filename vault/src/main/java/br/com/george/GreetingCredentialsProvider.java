package br.com.george;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Unremovable;
import io.quarkus.credentials.CredentialsProvider;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@Unremovable
@Named("credentials-provider")
public class GreetingCredentialsProvider implements CredentialsProvider,Runnable {
    String path = "/mnt/secrets-store/";
    String file = path + "db-password";

    @Inject
    AgroalDataSource defaultDataSource;
    @Inject
    Vertx vertx;
    private WorkerExecutor executor;

    @PostConstruct
    void init() {
    }

    @Override
    public void run() {
        for (;;) {
            System.out.println("Checking file");
            try (WatchService watchService = FileSystems.getDefault().newWatchService() ){
                Path spath = Paths.get(path);
                spath.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                WatchKey key;
                if ((key = watchService.take()) != null) {
                    System.out.println("Reseting");
                    defaultDataSource.flush(AgroalDataSource.FlushMode.ALL);
                    key.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private boolean started = false ;

    @Override
    public Map<String, String> getCredentials(String credentialsProviderName) {
        System.out.println("Getting credential");
        if (!started) {
            started = true;
            Thread.startVirtualThread(this);
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> map = null;
        try {
            map = mapper.readValue(Paths.get(file).toFile(), Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, String> properties = new HashMap<>();
        properties.put(USER_PROPERTY_NAME, (String) ((Map<?,?>)map.get("data")).get("username"));
        properties.put(PASSWORD_PROPERTY_NAME, (String) ((Map<?,?>)map.get("data")).get("password"));
        return properties;
    }

}
