#!/usr/bin/env python3
"""
Gerador de dataset sintético de produtos para testes de busca semântica.
Uso: python3 generate-dataset.py [quantidade]
Exemplo: python3 generate-dataset.py 1000
"""

import json
import random
import sys

# Produtos base com descrições realistas
# Distribuições de popularidade por categoria (min, max)
CATEGORY_POPULARITY = {
    "Eletrônicos": (500, 8000),    # Alta popularidade
    "Moda": (300, 6000),           # Alta popularidade
    "Casa": (200, 4000),           # Média popularidade
    "Esportes": (150, 3500),       # Média popularidade
    "Livros": (100, 2500),         # Baixa popularidade
    "Alimentos": (400, 5000)       # Alta popularidade
}

# Distribuições de qualidade por categoria (min, max)
CATEGORY_QUALITY = {
    "Eletrônicos": (3.8, 4.9),     # Alta qualidade
    "Moda": (3.5, 4.7),            # Média-alta qualidade
    "Casa": (3.7, 4.8),            # Alta qualidade
    "Esportes": (3.6, 4.6),        # Média qualidade
    "Livros": (4.0, 4.9),          # Muito alta qualidade
    "Alimentos": (3.4, 4.5)        # Média qualidade
}

BASE_PRODUCTS = [
    # Eletrônicos
    {"title": "Notebook", "description": "Computador portátil para trabalho e estudos", "category": "Eletrônicos"},
    {"title": "Smartphone", "description": "Telefone inteligente com câmera de alta resolução", "category": "Eletrônicos"},
    {"title": "Tablet", "description": "Dispositivo touch screen para entretenimento", "category": "Eletrônicos"},
    {"title": "Smartwatch", "description": "Relógio inteligente com monitoramento de saúde", "category": "Eletrônicos"},
    {"title": "Fone de Ouvido", "description": "Fone com cancelamento de ruído", "category": "Eletrônicos"},
    {"title": "Mouse", "description": "Mouse sem fio ergonômico", "category": "Eletrônicos"},
    {"title": "Teclado", "description": "Teclado mecânico retroiluminado", "category": "Eletrônicos"},
    {"title": "Monitor", "description": "Monitor LED Full HD", "category": "Eletrônicos"},
    
    # MODA E ACESSÓRIOS
    {"title": "Tênis Nike Air Max 270", "description": "Amortecimento de impacto, design moderno, ideal para corrida e caminhada", "category": "Moda"},
    {"title": "Jaqueta de couro Levi's", "description": "Couro legítimo, estilo motoqueiro, forros internos, zíperes metálicos", "category": "Moda"},
    {"title": "Relógio Casio G-Shock", "description": "Resistente a choques, à prova d'água 200m, cronômetro, múltiplos fusos", "category": "Moda"},
    {"title": "Bolsa Michael Kors", "description": "Couro legítimo, alça transversal, compartimentos internos, design sofisticado", "category": "Moda"},
    {"title": "Óculos Ray-Ban Aviator", "description": "Lentes polarizadas, proteção UV400, armação de metal dourado", "category": "Moda"},
    {"title": "Mochila Herschel", "description": "Para notebook até 15 polegadas, tecido resistente, compartimento acolchoado", "category": "Moda"},
    {"title": "Calça jeans Diesel", "description": "Slim fit, lavagem escura, tecido stretch confortável", "category": "Moda"},
    {"title": "Vestido longo floral", "description": "Tecido leve e fluido, ideal para verão, decote em V elegante", "category": "Moda"},
    {"title": "Cinto de couro Hugo Boss", "description": "Couro legítimo, fivela metálica, largura 3.5cm, estilo clássico", "category": "Moda"},
    {"title": "Carteira Tommy Hilfiger", "description": "Couro genuíno, múltiplos compartimentos para cartões, porta-moedas", "category": "Moda"},
    
    # CASA E DECORAÇÃO
    {"title": "Aspirador robô Roomba i7+", "description": "Mapeamento inteligente, esvaziamento automático, Wi-Fi, compatível Alexa", "category": "Casa"},
    {"title": "Purificador de ar Philips", "description": "Filtro HEPA, remove 99.97% partículas, sensor qualidade do ar", "category": "Casa"},
    {"title": "Cafeteira Nespresso Vertuo", "description": "Preparo automático, 5 tamanhos de xícara, sistema de cápsulas", "category": "Casa"},
    {"title": "Jogo de panelas Tramontina", "description": "Antiaderentes, 5 peças, cabo baquelite, indução, livre de PFOA", "category": "Casa"},
    {"title": "Edredom king size", "description": "300 fios, 100% algodão egípcio, hipoalergênico, macio e respirável", "category": "Casa"},
    
    # ESPORTES E FITNESS
    {"title": "Bicicleta ergométrica Kikos", "description": "8 níveis resistência, monitor LCD, suporta até 120kg", "category": "Esportes"},
    {"title": "Halteres ajustáveis", "description": "2 a 24kg por unidade, sistema de seleção rápida, base compacta", "category": "Esportes"},
    {"title": "Esteira elétrica Movement", "description": "Velocidade até 16km/h, inclinação elétrica, monitor cardíaco", "category": "Esportes"},
    {"title": "Colchonete de yoga premium", "description": "6mm espessura, material NBR, antiderrapante, alça para transporte", "category": "Esportes"},
    {"title": "Suplemento whey protein", "description": "Isolado 900g, zero lactose, 25g de proteína por dose, sabor chocolate", "category": "Esportes"},
    
    # LIVROS
    {"title": "Livro Sapiens", "description": "Yuval Noah Harari, história da humanidade, capa dura, 464 páginas", "category": "Livros"},
    {"title": "Box Harry Potter completo", "description": "7 volumes, J.K. Rowling, capa dura ilustrada, edição colecionador", "category": "Livros"},
    {"title": "Livro Hábitos Atômicos", "description": "James Clear, guia prático para criar bons hábitos, best-seller", "category": "Livros"},
    {"title": "Livro 1984", "description": "George Orwell, edição especial, tradução nova, análise crítica", "category": "Livros"},
    
    # ALIMENTOS E BEBIDAS
    {"title": "Café em grãos Pilão Reserva", "description": "1kg, torra média, notas chocolate e caramelo, arábica 100%", "category": "Alimentos"},
    {"title": "Azeite extra virgem português", "description": "500ml, primeira prensagem a frio, acidez 0.3%", "category": "Alimentos"},
    {"title": "Chocolate Lindt Excellence", "description": "70% cacau, tablete 100g, cacau sustentável belga", "category": "Alimentos"},
    {"title": "Mel puro de abelhas", "description": "500g, produção artesanal, sem aditivos, florada silvestre", "category": "Alimentos"},
]

# Variações para gerar produtos únicos
BRANDS = ["Samsung", "Apple", "Sony", "LG", "Dell", "HP", "Lenovo", "Asus", "Xiaomi", 
          "Nike", "Adidas", "Puma", "Reebok", "New Balance",
          "Levi's", "Calvin Klein", "Tommy Hilfiger", "Lacoste",
          "Ray-Ban", "Oakley", "Michael Kors", "Guess"]

COLORS = ["Preto", "Branco", "Azul", "Vermelho", "Verde", "Cinza", "Rosa", "Amarelo", 
          "Roxo", "Laranja", "Marrom", "Bege", "Prata", "Dourado"]

SIZES = ["P", "M", "G", "GG", "XG", "32GB", "64GB", "128GB", "256GB", "512GB", "1TB", "2TB"]

ADJECTIVES = ["Premium", "Pro", "Ultra", "Max", "Plus", "Lite", "Elite", "Essential", 
              "Classic", "Sport", "Deluxe", "Advanced", "Basic", "Special Edition"]

def generate_popularity_metrics(category, seed=None):
    """Gera métricas de popularidade realistas baseadas na categoria"""
    if seed is not None:
        random.seed(seed)
    
    # Popularidade (clicks)
    pop_min, pop_max = CATEGORY_POPULARITY[category]
    popularity = random.randint(pop_min, pop_max)
    
    # Qualidade (rating de 0 a 5)
    qual_min, qual_max = CATEGORY_QUALITY[category]
    quality = round(random.uniform(qual_min, qual_max), 1)
    
    # CTR (correlacionado com qualidade: produtos melhores têm CTR maior)
    # Base CTR: 0.02 a 0.12
    base_ctr = 0.02 + (quality - 3.0) * 0.05  # 3.0 stars = 2%, 5.0 stars = 12%
    # Adicionar variação aleatória ±30%
    ctr = base_ctr * random.uniform(0.7, 1.3)
    ctr = round(min(0.20, max(0.01, ctr)), 3)  # Limitar entre 1% e 20%
    
    return {
        "popularity": popularity,
        "quality": quality,
        "ctr": ctr
    }

def generate_product_variation(base_product, seed):
    """Gera uma variação do produto base com características únicas"""
    random.seed(seed)
    
    title = base_product["title"]
    description = base_product["description"]
    category = base_product["category"]
    
    # Adicionar marca aleatória
    if random.random() > 0.3:
        title = f"{random.choice(BRANDS)} {title}"
    
    # Adicionar cor aleatória
    if random.random() > 0.5:
        color = random.choice(COLORS)
        title += f" {color}"
        description += f" na cor {color.lower()}"
    
    # Adicionar tamanho/capacidade
    if random.random() > 0.5:
        size = random.choice(SIZES)
        description += f" - {size}"
    
    # Adicionar adjetivos à descrição
    variations = random.sample(ADJECTIVES, min(3, len(ADJECTIVES)))
    if variations:
        title += " " + " ".join(random.sample(variations, min(2, len(variations))))
    
    # Adicionar número de modelo ocasionalmente
    if random.random() > 0.7:
        title += f" Modelo {random.randint(100, 9999)}"
    
    # Gerar métricas de popularidade
    metrics = generate_popularity_metrics(category, seed + 1000)
    
    return {
        "title": title,
        "description": description,
        "category": category,
        "popularity": metrics["popularity"],
        "quality": metrics["quality"],
        "ctr": metrics["ctr"]
    }

def generate_dataset(num_products):
    """Gera dataset com número específico de produtos"""
    products = []
    
    # Adicionar todos os produtos base com métricas
    for i, base in enumerate(BASE_PRODUCTS):
        metrics = generate_popularity_metrics(base["category"], i)
        product = {
            "title": base["title"],
            "description": base["description"],
            "category": base["category"],
            "popularity": metrics["popularity"],
            "quality": metrics["quality"],
            "ctr": metrics["ctr"]
        }
        products.append(product)
    
    # Gerar variações até atingir o número desejado
    while len(products) < num_products:
        base = random.choice(BASE_PRODUCTS)
        variant = generate_product_variation(base, len(products))
        products.append(variant)
    
    return products[:num_products]

def main():
    # Determinar quantidade
    if len(sys.argv) > 1:
        try:
            num_products = int(sys.argv[1])
        except ValueError:
            print("❌ Erro: quantidade deve ser um número inteiro")
            sys.exit(1)
    else:
        num_products = 1000  # Padrão
    
    print(f"📝 Gerando dataset com {num_products} produtos...")
    
    # Gerar produtos
    products = generate_dataset(num_products)
    
    # Salvar como array JSON
    output_file = "data/products_synthetic.json"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(products, f, ensure_ascii=False, indent=2)
    
    print(f"✅ Dataset criado com sucesso!")
    print(f"   Arquivo: {output_file}")
    print(f"   Total: {len(products)} produtos")
    print(f"\n📊 Distribuição por categoria:")
    
    # Mostrar estatísticas
    categories = {}
    for product in products:
        cat = product["category"]
        categories[cat] = categories.get(cat, 0) + 1
    
    for cat, count in sorted(categories.items()):
        percentage = (count / len(products)) * 100
        print(f"   {cat}: {count} ({percentage:.1f}%)")
    
    # Estatísticas de métricas
    print(f"\n📈 Métricas de popularidade:")
    avg_popularity = sum(p["popularity"] for p in products) / len(products)
    avg_quality = sum(p["quality"] for p in products) / len(products)
    avg_ctr = sum(p["ctr"] for p in products) / len(products)
    
    print(f"   Popularidade média: {avg_popularity:.0f} clicks")
    print(f"   Qualidade média: {avg_quality:.1f} / 5.0")
    print(f"   CTR médio: {avg_ctr*100:.1f}%")
    
    print(f"\n💡 Para usar no código Java:")
    print(f'   List<String> products = DatasetLoader.loadFromJson("data/products_synthetic.json");')

if __name__ == "__main__":
    main()
