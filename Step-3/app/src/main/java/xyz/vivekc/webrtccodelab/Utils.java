package xyz.vivekc.webrtccodelab;

import com.google.gson.GsonBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

@SuppressWarnings("ALL")
public class Utils {

    static Utils instance;
    public static final String API_ENDPOINT = "https://global.xirsys.net";

    public static Utils getInstance() {
        if (instance == null) {
            instance = new Utils();
        }
        return instance;
    }

    private Retrofit retrofitInstance;

    TurnServer getRetrofitInstance() {
        if (retrofitInstance == null) {
            retrofitInstance = new Retrofit.Builder()
                    .client(
                        new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    )
                    .baseUrl(API_ENDPOINT)
                    .addConverterFactory(
                        GsonConverterFactory.create(
                            new GsonBuilder()
                                .setLenient()
                                .create()
                        )
                    )
                    .build();
        }
        return retrofitInstance.create(TurnServer.class);
    }

    public static String getRandomSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 5) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;
    }
}
