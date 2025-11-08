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
BASE_PRODUCTS = [
    # ELETRÔNICOS
    {"title": "Smartphone Samsung Galaxy S23 Ultra", "description": "Câmera de 200MP, tela AMOLED 6.8 polegadas, 5G, bateria de longa duração", "category": "Eletrônicos"},
    {"title": "Notebook Dell Inspiron 15", "description": "Intel Core i7, 16GB RAM, SSD 512GB, placa de vídeo NVIDIA dedicada", "category": "Eletrônicos"},
    {"title": "Smart TV LG 55 polegadas", "description": "4K OLED, HDR, WebOS, controle remoto com inteligência artificial", "category": "Eletrônicos"},
    {"title": "Fone de ouvido Sony WH-1000XM5", "description": "Cancelamento de ruído ativo, Bluetooth 5.2, bateria 30 horas", "category": "Eletrônicos"},
    {"title": "Apple iPad Pro", "description": "Chip M2, tela Liquid Retina XDR, compatível com Apple Pencil", "category": "Eletrônicos"},
    {"title": "Câmera Canon EOS R6", "description": "Mirrorless full frame, 24.2MP, vídeo 4K 60fps, estabilização", "category": "Eletrônicos"},
    {"title": "Console PlayStation 5", "description": "SSD ultra-rápido, controle DualSense, ray tracing, gráficos 4K", "category": "Eletrônicos"},
    {"title": "Smartwatch Apple Watch Series 9", "description": "Monitor cardíaco, GPS, rastreamento de sono, resistente à água", "category": "Eletrônicos"},
    {"title": "Kindle Paperwhite", "description": "Tela sem reflexo, luz ajustável, 16GB, à prova d'água", "category": "Eletrônicos"},
    {"title": "Caixa de som JBL Flip 6", "description": "Bluetooth portátil, som 360 graus, à prova d'água, 12h bateria", "category": "Eletrônicos"},
    
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

def generate_product_variation(base_product, index):
    """Gera uma variação única de um produto base"""
    title = base_product["title"]
    description = base_product["description"]
    
    # Adicionar variações aleatórias ao título
    variations = []
    
    if random.random() > 0.3:
        variations.append(random.choice(ADJECTIVES))
    
    if random.random() > 0.5:
        variations.append(random.choice(COLORS))
    
    if random.random() > 0.4:
        variations.append(random.choice(SIZES))
    
    if random.random() > 0.6:
        variations.append(random.choice(BRANDS))
    
    # Adicionar variações ao título
    if variations:
        title += " " + " ".join(random.sample(variations, min(2, len(variations))))
    
    # Adicionar número de modelo ocasionalmente
    if random.random() > 0.7:
        title += f" Modelo {random.randint(100, 9999)}"
    
    return {
        "title": title,
        "description": description,
        "category": base_product["category"]
    }

def generate_dataset(num_products):
    """Gera dataset com número específico de produtos"""
    products = []
    
    # Adicionar todos os produtos base
    products.extend(BASE_PRODUCTS)
    
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
    
    print(f"\n💡 Para usar no código Java:")
    print(f'   List<String> products = DatasetLoader.loadFromJson("data/products_synthetic.json");')

if __name__ == "__main__":
    main()
