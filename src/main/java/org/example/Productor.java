package org.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class Productor {
    private static final String QUEUE_CEDULA = "cedula_queue";
    private static final String QUEUE_PLACA = "placa_queue";

    private static final String BASE_URL_SRI = "https://srienlinea.sri.gob.ec";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("🧾 Ingrese la cédula para enviar: ");
        String cedula = scanner.nextLine();

        System.out.print("🧾 Ingrese el RUC: ");
        String ruc = scanner.nextLine();

        if (verificarExistenciaRUC(ruc)) {
            obtenerDatosContribuyente(ruc);
        } else {
            System.out.println("❌ El RUC ingresado no existe.");
        }

        System.out.print("🧾 Ingrese la placa: ");
        String placa = scanner.nextLine();
        obtenerDatosVehiculo(placa);

        try {
            enviarCedula(cedula);
            enviarPlaca(placa);
        } catch (Exception e) {
            System.out.println("❌ Error al enviar datos a RabbitMQ:");
            e.printStackTrace();
        }
    }





    public static void enviarCedula(String cedula) throws Exception {
        enviarMensaje(QUEUE_CEDULA, cedula);
        System.out.println("✅ Cédula enviada correctamente: " + cedula);
    }

    public static void enviarPlaca(String placa) throws Exception {
        enviarMensaje(QUEUE_PLACA, placa);
        System.out.println("✅ Placa enviada correctamente: " + placa);
    }

    private static void enviarMensaje(String queueName, String mensaje) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // RabbitMQ debe estar activo

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.queueDeclare(queueName, false, false, false, null);
            channel.basicPublish("", queueName, null, mensaje.getBytes());
        }
    }

    public static boolean verificarExistenciaRUC(String ruc) {
        String url = BASE_URL_SRI + "/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc?numeroRuc=" + ruc;
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Boolean.parseBoolean(response.body());
        } catch (IOException | InterruptedException e) {
            System.out.println("❌ Error al verificar RUC.");
            e.printStackTrace();
            return false;
        }
    }

    public static void obtenerDatosContribuyente(String ruc) {
        String redisKey = "contribuyente:" + ruc;

        try (Jedis jedis = new Jedis("localhost", 6379)) {
            // 1. Buscar en Redis
            if (jedis.exists(redisKey)) {
                String cachedData = jedis.get(redisKey);
                System.out.println("✅ (Cache) Datos del contribuyente:\n" + cachedData);
                return;
            }

            // 2. No existe en cache → llamar a la API
            String url = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc?ruc=" + ruc;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            if (body.contains("\"tipoContribuyente\":\"PERSONA NATURAL\"")) {
                System.out.println("✅ Es persona natural.");
                System.out.println("📄 Datos del contribuyente:\n" + body);
            } else {
                System.out.println("⚠️ No es una persona natural.");
            }

            // 3. Guardar en Redis por 1 hora (3600 segundos)
            jedis.setex(redisKey, 3600, body);
            System.out.println("🧠 Guardado en caché Redis con clave: " + redisKey);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void obtenerDatosVehiculo(String placa) {
        String redisKey = "vehiculo:" + placa;

        try (Jedis jedis = new Jedis("localhost", 6379)) {
            // 1. Buscar en Redis
            if (jedis.exists(redisKey)) {
                String cachedData = jedis.get(redisKey);
                System.out.println("✅ (Cache) Datos del vehículo:\n" + cachedData);
                return;
            }

            // 2. No existe en cache → llamar a la API
            String url = BASE_URL_SRI + "/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn=" + placa;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (!body.contains("null")) {
                System.out.println("🚗 Datos del vehículo:");
                System.out.println(body);

                // 3. Guardar en Redis por 1 hora (3600 segundos)
                jedis.setex(redisKey, 3600, body);
                System.out.println("🧠 Guardado en caché Redis con clave: " + redisKey);

            } else {
                System.out.println("❌ No se encontró el vehículo con placa: " + placa);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("❌ Error al obtener datos del vehículo.");
            e.printStackTrace();
        }
    }

}
