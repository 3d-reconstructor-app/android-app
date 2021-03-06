package com.example.a3dmodel;

import java.io.*;

import okhttp3.*;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.a3dmodel.exeption.AppException;
import com.example.a3dmodel.visualisation.PlyParser;

public class Client {
    private static final String IMAGE_STRING = "image/png";
    private static final String ZIP_STRING = "application/zip";
    private static final String JSON_STRING = "application/json";
    private static final MediaType JSON = MediaType.get(JSON_STRING);
    private static final MediaType IMAGE = MediaType.get(IMAGE_STRING);
    private static final MediaType ZIP = MediaType.get(ZIP_STRING);

    public static void httpClientRequest(List<File> files, File result) throws AppException, IOException {
        String token = UUID.randomUUID().toString();
        String url = "https://fc61-176-53-196-130.eu.ngrok.io/";
        System.out.println(token);
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(20, TimeUnit.MINUTES)
                .writeTimeout(20, TimeUnit.MINUTES)
                .connectTimeout(20, TimeUnit.MINUTES)
                .addInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public Response intercept(@NotNull Chain chain) throws IOException {
                        String credential = Credentials.basic("username", "password");
                        Request request = chain.request();
                        Request authenticatedRequest = request.newBuilder()
                                .header("Authorization", credential).build();
                        return chain.proceed(authenticatedRequest);
                    }
                })
                .build();
        File zipFile = Files.createTempFile(null, ".zip").toFile();
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
        for (File file : files) {
            ZipEntry e = new ZipEntry(file.getName());
            FileInputStream fin = new FileInputStream(file);
            out.putNextEntry(e);
            int length;
            byte[] buffer = new byte[1024];
            while ((length = fin.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.closeEntry();
        }
        out.close();
        System.out.println(files.stream().mapToLong(File::length).sum());
        System.out.println(zipFile.length());


        initializationHTTPPOSTRequest(client, url, token, 1, zipFile.getName());
        sendFileHTTPPOSTRequest(client, url, token, zipFile, ZIP);
        File tempPlyFile = Files.createTempFile(null, ".ply").toFile();
        getFileHTTPGETRequest(client, url, token, tempPlyFile);
        PlyParser.rewritePlyFile(tempPlyFile, result);
    }

    private static void initializationHTTPPOSTRequest(OkHttpClient client, String url, String token, long numberOfFiles, String zipFileName) throws AppException {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("id", token);
            jsonObject.put("count", numberOfFiles);
            jsonObject.put("name", zipFileName);
        } catch (JSONException e) {
            throw new AppException("Can't make json to initialize reconstruction", e);
        }

        RequestBody body = RequestBody.create(jsonObject.toString(), JSON);
        Request requestIni = new Request.Builder()
                .url(HttpUrl.get(url + token + ".json"))
                .post(body)
                .build();

        try (Response response = client.newCall(requestIni).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Send initialization: Ok");
            }
        } catch (IOException e) {
            throw new AppException("Can't make initialization request", e);
        }

    }

    private static void sendFileHTTPPOSTRequest(OkHttpClient client, String url, String token, File file, MediaType mediaType) throws AppException {
        RequestBody body = RequestBody.create(file, mediaType);
        Request requestIni = new Request.Builder()
                .url(HttpUrl.get(url + token + "/images/" + file.getName()))
                .post(body)
                .build();
        try (Response response = client.newCall(requestIni).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Send file " + file.getName() + " : Ok");
            }
        } catch (IOException e) {
            throw new AppException("Can't send file " + file.getName(), e);
        }
    }

    private static void getFileHTTPGETRequest(OkHttpClient client, String url, String token, File result) throws AppException {
        Request requestGet = new Request.Builder()
                .url(HttpUrl.get(url + "resources/" + token + "/res.ply"))
                .get()
                .build();
        try (Response response = client.newCall(requestGet).execute()) {
            if (response.isSuccessful()) {
                try (FileOutputStream stream = new FileOutputStream(result)) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        stream.write(body.bytes());
                    }
                    System.out.println("Get file " + result.getName() + " : Ok");
                }
            } else {
                System.out.println("wtf");
            }
        } catch (IOException e) {
            throw new AppException("Can't get result", e);
        }
    }
}
