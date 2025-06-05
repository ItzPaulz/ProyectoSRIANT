package org.example;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class Productor {
    private final static String QUEUE_NAME = "cedula_queue";
    private final static String QUEUE_NAME1 = "placa_queue";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("🧾 Ingrese la cédula para enviar: ");
        String cedula = scanner.nextLine();
        System.out.print("🧾 Ingrese la ruc para ver: ");
        String ruc = scanner.nextLine();
        verificarExistenciaRUC(ruc);
        obtenerDatosContribuyente(ruc);
        System.out.print("🧾 Ingrese la placa: ");
        String placa = scanner.nextLine();
        obtenerDatosVehiculo(placa);

        try {
            enviarCedula(cedula);
            enviarplaca(placa);
        } catch (Exception e) {
            System.out.println("❌ Error al enviar la cédula y placa");
            e.printStackTrace();
        }
    }

    public static void enviarCedula(String cedula) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // Asegúrate que RabbitMQ esté corriendo

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            channel.basicPublish("", QUEUE_NAME, null, cedula.getBytes());
            System.out.println("✅ Cédula enviada correctamente: " + cedula);
        }
    }
    public static void enviarplaca(String placa) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // Asegúrate que RabbitMQ esté corriendo

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME1, false, false, false, null);
            channel.basicPublish("", QUEUE_NAME1, null, placa.getBytes());
            System.out.println("✅ Cédula enviada correctamente: " + placa);
        }
    }
    public static boolean verificarExistenciaRUC(String ruc) {
        String url = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc?numeroRuc=" + ruc;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Boolean.parseBoolean(response.body());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void obtenerDatosContribuyente(String ruc) {
        String url = "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc?ruc=" + ruc;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (body.contains("\"tipoContribuyente\":\"PERSONA NATURAL\"")) {
                System.out.println("✅ Es persona natural.");
                System.out.println("📄 Datos del contribuyente:\n" + body);
            } else {
                System.out.println("⚠️ No es una persona natural.");
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    public static void obtenerDatosVehiculo(String placa) {

        String url = "https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn?numeroPlacaCampvCpn=" + placa;


        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (!body.contains("null")) {
                System.out.println("🚗 Datos del vehículo:");
                System.out.println(body);
            } else {
                System.out.println("❌ No se encontró el vehículo con placa: " + placa);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}