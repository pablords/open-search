# 📊 Dataset com Métricas de Popularidade

## 📝 Visão Geral

O dataset sintético foi atualizado para incluir métricas reais de popularidade, qualidade e CTR (Click-Through Rate) para cada produto. Essas métricas são usadas pelas **features #15, #16 e #17** do sistema LTR (Learning to Rank).

## 🆕 Novos Campos Adicionados

### 1. **popularity** (int)
- **Descrição**: Número de clicks/visualizações do produto
- **Faixa de valores**: Varia por categoria
  - **Eletrônicos**: 500 - 8.000 clicks (alta popularidade)
  - **Moda**: 300 - 6.000 clicks (alta popularidade)
  - **Alimentos**: 400 - 5.000 clicks (alta popularidade)
  - **Casa**: 200 - 4.000 clicks (média popularidade)
  - **Esportes**: 150 - 3.500 clicks (média popularidade)
  - **Livros**: 100 - 2.500 clicks (baixa popularidade)
- **Uso no LTR**: Feature #15 - produtos mais visualizados tendem a ser mais relevantes

### 2. **quality** (float)
- **Descrição**: Rating médio do produto (estrelas)
- **Faixa de valores**: 3.0 a 5.0 (varia por categoria)
  - **Livros**: 4.0 - 4.9 (muito alta qualidade)
  - **Casa**: 3.7 - 4.8 (alta qualidade)
  - **Eletrônicos**: 3.8 - 4.9 (alta qualidade)
  - **Moda**: 3.5 - 4.7 (média-alta qualidade)
  - **Esportes**: 3.6 - 4.6 (média qualidade)
  - **Alimentos**: 3.4 - 4.5 (média qualidade)
- **Uso no LTR**: Feature #16 - produtos bem avaliados são priorizados

### 3. **ctr** (float)
- **Descrição**: Click-Through Rate (taxa de conversão de impressão para click)
- **Faixa de valores**: 0.01 a 0.20 (1% a 20%)
- **Correlação**: CTR é correlacionado com qualidade
  - Produtos com quality = 3.0 → CTR base ≈ 2%
  - Produtos com quality = 5.0 → CTR base ≈ 12%
  - Variação aleatória de ±30% aplicada
- **Uso no LTR**: Feature #17 - produtos com alto CTR convertem melhor

## 📈 Estatísticas do Dataset (100 produtos)

```
Distribuição por categoria:
   Alimentos: 10 (10.0%)
   Casa: 11 (11.0%)
   Eletrônicos: 21 (21.0%)
   Esportes: 24 (24.0%)
   Livros: 7 (7.0%)
   Moda: 27 (27.0%)

Métricas de popularidade:
   Popularidade média: 2791 clicks
   Qualidade média: 4.2 / 5.0
   CTR médio: 7.8%
```

## 🔄 Alterações no Código

### 1. **generate-dataset.py**

#### Adicionadas constantes de distribuição:
```python
CATEGORY_POPULARITY = {
    "Eletrônicos": (500, 8000),
    "Moda": (300, 6000),
    # ... outras categorias
}

CATEGORY_QUALITY = {
    "Eletrônicos": (3.8, 4.9),
    "Moda": (3.5, 4.7),
    # ... outras categorias
}
```

#### Nova função para gerar métricas:
```python
def generate_popularity_metrics(category, seed=None):
    """Gera métricas de popularidade realistas baseadas na categoria"""
    popularity = random.randint(pop_min, pop_max)
    quality = round(random.uniform(qual_min, qual_max), 1)
    
    # CTR correlacionado com qualidade
    base_ctr = 0.02 + (quality - 3.0) * 0.05
    ctr = base_ctr * random.uniform(0.7, 1.3)
    
    return {
        "popularity": popularity,
        "quality": quality,
        "ctr": ctr
    }
```

### 2. **FeatureExtractor.java**

#### ❌ Removido: Métodos de simulação
```java
// ANTES (REMOVIDO):
private double simulatePopularity(int docNumber) { ... }
private double simulateQuality(int docNumber) { ... }
private double simulateCTR(int docNumber, String category) { ... }
```

#### ✅ Adicionado: Leitura de campos reais
```java
// NOVO: Extrai valores reais do documento OpenSearch
double popularity = getDoubleFromSource(result.getSource(), "popularity", 1000.0);
double quality = getDoubleFromSource(result.getSource(), "quality", 4.0);
double ctr = getDoubleFromSource(result.getSource(), "ctr", 0.05);

builder.add("popularity", popularity);
builder.add("quality", quality);
builder.add("ctr", ctr);
```

#### Novo método auxiliar:
```java
private double getDoubleFromSource(Map<String, Object> source, String field, double defaultValue) {
    if (source == null || !source.containsKey(field)) {
        return defaultValue;
    }
    
    Object value = source.get(field);
    if (value instanceof Number) {
        return ((Number) value).doubleValue();
    }
    
    // Tentar converter string para número
    if (value instanceof String) {
        try {
            return Double.parseDouble((String) value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    return defaultValue;
}
```

## 🎯 Impacto no Sistema LTR

### Antes (Simulado)
- Features #15, #16, #17 eram calculadas com funções matemáticas fictícias
- Não refletiam comportamento real dos usuários
- Útil apenas para demonstração

### Depois (Real)
- Features baseadas em dados reais do dataset
- Refletem padrões de comportamento por categoria:
  - Eletrônicos: Alta popularidade, alta qualidade, bom CTR
  - Livros: Baixa popularidade, muito alta qualidade, CTR moderado
  - Moda: Alta popularidade, qualidade variável, CTR correlacionado
- Pronto para integração com analytics reais em produção

## 🚀 Como Usar

### Regenerar Dataset
```bash
python3 generate-dataset.py 100
```

### Verificar Métricas
```bash
head -30 data/products_synthetic.json
```

### Exemplo de Produto
```json
{
  "title": "Notebook",
  "description": "Computador portátil para trabalho e estudos",
  "category": "Eletrônicos",
  "popularity": 7417,
  "quality": 4.2,
  "ctr": 0.099
}
```

## 📊 Integração com OpenSearch

Os novos campos são indexados automaticamente no OpenSearch quando você carrega o dataset:

```java
// No código Java, as métricas são extraídas diretamente do documento:
Map<String, Object> source = hit.getSourceAsMap();
double popularity = (double) source.getOrDefault("popularity", 1000.0);
double quality = (double) source.getOrDefault("quality", 4.0);
double ctr = (double) source.getOrDefault("ctr", 0.05);
```

## 🔮 Próximos Passos (Produção)

Para usar dados reais de analytics em produção:

1. **Conectar Analytics**: Integrar com Google Analytics, Adobe Analytics, ou similar
2. **Pipeline de Atualização**: Criar job para atualizar métricas diariamente
3. **Dados Históricos**: Usar últimos 30/90 dias para calcular médias
4. **A/B Testing**: Comparar ranking com/sem features de popularidade
5. **Monitoramento**: Acompanhar correlação entre features e conversão

## 📚 Referências

- **Arquivo do Dataset**: `data/products_synthetic.json`
- **Script Gerador**: `generate-dataset.py`
- **Extrator de Features**: `src/main/java/com/pablords/opensearch/FeatureExtractor.java`
- **Documentação LTR**: `LTR-ARCHITECTURE.md`
