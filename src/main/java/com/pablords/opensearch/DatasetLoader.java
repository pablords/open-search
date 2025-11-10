package com.pablords.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Carregador de datasets de produtos para testes de busca semântica.
 * Suporta formatos JSON, JSONL (JSON Lines) e CSV.
 */
public class DatasetLoader {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Carrega produtos de arquivo JSON Lines (formato Amazon)
   * 
   * @param filePath Caminho do arquivo (.json ou .json.gz)
   * @param limit    Limite de produtos a carregar (0 = todos)
   * @return Lista de descrições de produtos
   */
  public static List<String> loadFromJsonLines(String filePath, int limit) throws IOException {
    List<String> products = new ArrayList<>();

    InputStream inputStream = new FileInputStream(filePath);

    // Se for .gz, descompactar
    if (filePath.endsWith(".gz")) {
      inputStream = new GZIPInputStream(inputStream);
    }

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

      String line;
      int count = 0;

      while ((line = reader.readLine()) != null) {
        if (limit > 0 && count >= limit) {
          break;
        }

        try {
          JsonNode json = objectMapper.readTree(line);
          String product = extractProductDescription(json);

          if (product != null && !product.trim().isEmpty()) {
            products.add(product);
            count++;
          }
        } catch (Exception e) {
          // Ignorar linhas com erro
          System.err.println("Erro ao processar linha: " + e.getMessage());
        }
      }
    }

    System.out.println("✓ Carregados " + products.size() + " produtos de " + filePath);
    return products;
  }

  /**
   * Extrai descrição do produto de um JSON da Amazon
   */
  private static String extractProductDescription(JsonNode json) {
    StringBuilder description = new StringBuilder();

    // Título (obrigatório)
    if (json.has("title")) {
      description.append(json.get("title").asText());
    }

    // Descrição adicional
    if (json.has("description")) {
      JsonNode desc = json.get("description");
      if (desc.isArray() && desc.size() > 0) {
        description.append(". ").append(desc.get(0).asText());
      } else if (desc.isTextual()) {
        description.append(". ").append(desc.asText());
      }
    }

    // Features/características
    if (json.has("feature")) {
      JsonNode features = json.get("feature");
      if (features.isArray()) {
        for (int i = 0; i < Math.min(3, features.size()); i++) {
          description.append(". ").append(features.get(i).asText());
        }
      }
    }

    // Categoria
    if (json.has("category")) {
      JsonNode category = json.get("category");
      if (category.isArray() && category.size() > 0) {
        description.append(" (").append(category.get(category.size() - 1).asText()).append(")");
      }
    }

    if (json.has("popularity")) {
      description.append(" Popularidade: ").append(json.get("popularity").asText());
    }
    if (json.has("quality")) {
      description.append(" Qualidade: ").append(json.get("quality").asText());
    }
    if (json.has("ctr")) {
      description.append(" CTR: ").append(json.get("ctr").asText());
    }

    return description.toString().trim();
  }

  /**
   * Carrega produtos de arquivo CSV simples
   * 
   * @param filePath          Caminho do arquivo CSV
   * @param descriptionColumn Índice da coluna com descrição (0-based)
   * @param hasHeader         Se o arquivo tem cabeçalho
   * @param limit             Limite de produtos (0 = todos)
   */
  public static List<String> loadFromCSV(String filePath, int descriptionColumn,
      boolean hasHeader, int limit) throws IOException {
    List<String> products = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(
        new FileReader(filePath, StandardCharsets.UTF_8))) {

      String line;
      int count = 0;
      boolean firstLine = true;

      while ((line = reader.readLine()) != null) {
        if (firstLine && hasHeader) {
          firstLine = false;
          continue;
        }

        if (limit > 0 && count >= limit) {
          break;
        }

        String[] columns = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)"); // Split CSV respeitando aspas

        if (columns.length > descriptionColumn) {
          String description = columns[descriptionColumn]
              .replaceAll("^\"|\"$", "") // Remove aspas
              .trim();

          if (!description.isEmpty()) {
            products.add(description);
            count++;
          }
        }
      }
    }

    System.out.println("✓ Carregados " + products.size() + " produtos de " + filePath);
    return products;
  }

  /**
   * Exemplo de uso com dataset da Amazon
   */
  public static void main(String[] args) {
    try {
      // Exemplo 1: Carregar dataset da Amazon (JSON Lines)
      // Download: wget
      // http://deepyeti.ucsd.edu/jianmo/amazon/metaFiles2/meta_Electronics.json.gz

      String amazonFile = "data/meta_Electronics.json.gz";
      if (new File(amazonFile).exists()) {
        System.out.println("Carregando produtos da Amazon...");
        List<String> products = loadFromJsonLines(amazonFile, 100); // Limita a 100

        System.out.println("\n📦 Primeiros 5 produtos:");
        for (int i = 0; i < Math.min(5, products.size()); i++) {
          System.out.println((i + 1) + ". " + products.get(i));
        }
      } else {
        System.out.println("❌ Arquivo não encontrado: " + amazonFile);
        System.out.println("\n💡 Para baixar o dataset:");
        System.out.println("   mkdir -p data");
        System.out.println("   cd data");
        System.out.println("   wget http://deepyeti.ucsd.edu/jianmo/amazon/metaFiles2/meta_Electronics.json.gz");
      }

      // Exemplo 2: CSV
      String csvFile = "data/products.csv";
      if (new File(csvFile).exists()) {
        System.out.println("\nCarregando produtos do CSV...");
        List<String> products = loadFromCSV(csvFile, 1, true, 50); // Coluna 1, com header
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
