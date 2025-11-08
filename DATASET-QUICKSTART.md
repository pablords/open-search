# 🚀 Como Usar o Dataset Sintético

## Geração Rápida

```bash
# Gerar 1000 produtos (recomendado)
python3 generate-dataset.py 1000

# Outras quantidades:
python3 generate-dataset.py 100     # Testes rápidos
python3 generate-dataset.py 5000    # Teste de performance
python3 generate-dataset.py 10000   # Teste completo
```

## Usar no Código Java

```java
// Carregar todos os produtos
List<String> products = DatasetLoader.loadFromJsonLines(
    "data/products_synthetic.json", 
    0  // 0 = todos
);

// Ou limitar quantidade
List<String> products = DatasetLoader.loadFromJsonLines(
    "data/products_synthetic.json", 
    500  // Apenas 500
);
```

## Queries de Teste Sugeridas

```java
// Eletrônicos
searchByVector(client, model, "celular com boa câmera");
searchByVector(client, model, "notebook para trabalho");
searchByVector(client, model, "fone sem fio com cancelamento de ruído");

// Moda
searchByVector(client, model, "tênis para corrida");
searchByVector(client, model, "relógio resistente à água");
searchByVector(client, model, "bolsa de couro elegante");

// Casa
searchByVector(client, model, "aspirador automático");
searchByVector(client, model, "purificador de ar");
searchByVector(client, model, "cafeteira para espresso");

// Esportes
searchByVector(client, model, "equipamento para malhar em casa");
searchByVector(client, model, "suplemento proteico");

// Livros
searchByVector(client, model, "livro sobre história");
searchByVector(client, model, "romance de ficção");

// Alimentos
searchByVector(client, model, "café premium");
searchByVector(client, model, "chocolate importado");
```

## Dataset Gerado

- **Formato**: JSON Lines (um JSON por linha)
- **Campos**: title, description, category
- **Categorias**: Eletrônicos, Moda, Casa, Esportes, Livros, Alimentos
- **Variações**: Cores, tamanhos, marcas, adjetivos, modelos

Pronto para uso com busca semântica! 🎯
