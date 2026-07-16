import 'package:flutter_test/flutter_test.dart';
import 'package:nutriscan_ai/core/models/nutrition.dart';

void main() {
  group('Nutrition', () {
    test('parsea JSON del backend con números enteros y decimales', () {
      final n = Nutrition.fromJson({
        'calories': 650,
        'proteinG': 45.5,
        'carbsG': 65,
        'fatG': 18,
        'fiberG': 6,
        'sodiumMg': 480,
        'sugarG': 4,
      });
      expect(n.calories, 650);
      expect(n.proteinG, 45.5);
      expect(n.sodiumMg, 480);
    });

    test('tolera campos ausentes con valores en 0', () {
      final n = Nutrition.fromJson({});
      expect(n.calories, 0);
      expect(n.proteinG, 0);
    });
  });

  group('FoodAnalysis', () {
    test('parsea la respuesta completa del análisis IA', () {
      final a = FoodAnalysis.fromJson({
        'dishName': 'Pollo con arroz',
        'portion': '1 plato grande',
        'confidence': 0.92,
        'total': {'calories': 650},
        'items': [
          {
            'name': 'Pollo',
            'quantity': '150 g',
            'nutrition': {'calories': 248},
          }
        ],
        'remainingFreeScans': 2,
      });
      expect(a.dishName, 'Pollo con arroz');
      expect(a.items, hasLength(1));
      expect(a.items.first.nutrition.calories, 248);
      expect(a.remainingFreeScans, 2);
    });
  });

  group('MealType', () {
    test('mapea valores de la API en ambos sentidos', () {
      expect(MealType.breakfast.apiValue, 'BREAKFAST');
      expect(MealTypeX.fromApi('LUNCH'), MealType.lunch);
      expect(MealTypeX.fromApi('desconocido'), MealType.snack);
      expect(MealType.dinner.label, 'Cena');
    });
  });
}
