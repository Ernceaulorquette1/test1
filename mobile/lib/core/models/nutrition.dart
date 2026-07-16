/// Modelos compartidos de nutrición (espejo de los DTO del backend).
class Nutrition {
  final double calories, proteinG, carbsG, fatG, fiberG, sodiumMg, sugarG;

  const Nutrition({
    this.calories = 0,
    this.proteinG = 0,
    this.carbsG = 0,
    this.fatG = 0,
    this.fiberG = 0,
    this.sodiumMg = 0,
    this.sugarG = 0,
  });

  factory Nutrition.fromJson(Map<String, dynamic> json) => Nutrition(
        calories: _d(json['calories']),
        proteinG: _d(json['proteinG']),
        carbsG: _d(json['carbsG']),
        fatG: _d(json['fatG']),
        fiberG: _d(json['fiberG']),
        sodiumMg: _d(json['sodiumMg']),
        sugarG: _d(json['sugarG']),
      );

  Map<String, dynamic> toJson() => {
        'calories': calories,
        'proteinG': proteinG,
        'carbsG': carbsG,
        'fatG': fatG,
        'fiberG': fiberG,
        'sodiumMg': sodiumMg,
        'sugarG': sugarG,
      };

  static double _d(dynamic v) => v == null ? 0 : (v as num).toDouble();
}

class FoodItem {
  final String name;
  final String quantity;
  final Nutrition nutrition;

  const FoodItem({required this.name, required this.quantity, required this.nutrition});

  factory FoodItem.fromJson(Map<String, dynamic> json) => FoodItem(
        name: json['name'] ?? '',
        quantity: json['quantity'] ?? '',
        nutrition: Nutrition.fromJson(json['nutrition'] ?? {}),
      );

  Map<String, dynamic> toJson() =>
      {'name': name, 'quantity': quantity, 'nutrition': nutrition.toJson()};
}

/// Resultado del análisis IA de una foto.
class FoodAnalysis {
  final String dishName;
  final String portion;
  final double confidence;
  final Nutrition total;
  final List<FoodItem> items;
  final int remainingFreeScans;

  const FoodAnalysis({
    required this.dishName,
    required this.portion,
    required this.confidence,
    required this.total,
    required this.items,
    required this.remainingFreeScans,
  });

  factory FoodAnalysis.fromJson(Map<String, dynamic> json) => FoodAnalysis(
        dishName: json['dishName'] ?? 'Desconocido',
        portion: json['portion'] ?? '',
        confidence: Nutrition._d(json['confidence']),
        total: Nutrition.fromJson(json['total'] ?? {}),
        items: ((json['items'] ?? []) as List)
            .map((e) => FoodItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        remainingFreeScans: (json['remainingFreeScans'] ?? 0) as int,
      );
}

enum MealType { breakfast, lunch, dinner, snack }

extension MealTypeX on MealType {
  String get apiValue => name.toUpperCase();
  String get label => switch (this) {
        MealType.breakfast => 'Desayuno',
        MealType.lunch => 'Almuerzo',
        MealType.dinner => 'Cena',
        MealType.snack => 'Snack',
      };
  static MealType fromApi(String v) =>
      MealType.values.firstWhere((m) => m.apiValue == v, orElse: () => MealType.snack);
}

class Meal {
  final String id;
  final MealType mealType;
  final String name;
  final String? portion;
  final DateTime eatenAt;
  final Nutrition total;
  final List<FoodItem> items;

  const Meal({
    required this.id,
    required this.mealType,
    required this.name,
    this.portion,
    required this.eatenAt,
    required this.total,
    this.items = const [],
  });

  factory Meal.fromJson(Map<String, dynamic> json) => Meal(
        id: json['id'] ?? '',
        mealType: MealTypeX.fromApi(json['mealType'] ?? 'SNACK'),
        name: json['name'] ?? '',
        portion: json['portion'],
        eatenAt: DateTime.tryParse(json['eatenAt'] ?? '') ?? DateTime.now(),
        total: Nutrition.fromJson(json['total'] ?? {}),
        items: ((json['items'] ?? []) as List)
            .map((e) => FoodItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}
