import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/nutrition.dart';
import 'api_client.dart';

/// Capa de datos: repositorios que hablan con la API REST de NutriScan.
class MealRepository {
  MealRepository(this._dio);
  final Dio _dio;

  Future<FoodAnalysis> analyzePhoto(String path) async {
    final form = FormData.fromMap({
      'image': await MultipartFile.fromFile(path, filename: 'meal.jpg'),
    });
    final res = await _dio.post('/api/v1/meals/analyze', data: form);
    return FoodAnalysis.fromJson(res.data);
  }

  Future<void> saveMeal({
    required MealType type,
    required String name,
    String? portion,
    required Nutrition total,
    List<FoodItem> items = const [],
  }) async {
    await _dio.post('/api/v1/meals', data: {
      'mealType': type.apiValue,
      'name': name,
      'portion': portion,
      'total': total.toJson(),
      'items': items.map((e) => e.toJson()).toList(),
    });
  }

  Future<({double totalCalories, int? targetCalories, Nutrition totals, List<Meal> meals})>
      dayHistory(DateTime date) async {
    final d = date.toIso8601String().substring(0, 10);
    final res = await _dio.get('/api/v1/meals/history', queryParameters: {'date': d});
    return (
      totalCalories: ((res.data['totalCalories'] ?? 0) as num).toDouble(),
      targetCalories: res.data['targetCalories'] as int?,
      totals: Nutrition.fromJson(res.data['totals'] ?? {}),
      meals: ((res.data['meals'] ?? []) as List)
          .map((e) => Meal.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<void> deleteMeal(String id) => _dio.delete('/api/v1/meals/$id');
}

class StatsRepository {
  StatsRepository(this._dio);
  final Dio _dio;

  Future<Map<String, dynamic>> stats(String range) async =>
      (await _dio.get('/api/v1/stats', queryParameters: {'range': range})).data;

  Future<void> logWeight(double kg) =>
      _dio.post('/api/v1/stats/weight', data: {'weightKg': kg});
}

class WaterRepository {
  WaterRepository(this._dio);
  final Dio _dio;

  Future<({int totalMl, int goalMl})> today() async {
    final res = await _dio.get('/api/v1/water/today');
    return (totalMl: res.data['totalMl'] as int, goalMl: res.data['goalMl'] as int);
  }

  Future<({int totalMl, int goalMl})> add(int ml) async {
    final res = await _dio.post('/api/v1/water', data: {'ml': ml});
    return (totalMl: res.data['totalMl'] as int, goalMl: res.data['goalMl'] as int);
  }
}

class ChatRepository {
  ChatRepository(this._dio);
  final Dio _dio;

  Future<String> send(String message) async =>
      (await _dio.post('/api/v1/chat', data: {'message': message})).data['reply'];

  Future<List<({String role, String content})>> history() async {
    final res = await _dio.get('/api/v1/chat/history');
    return (res.data as List)
        .map((e) => (role: e['role'] as String, content: e['content'] as String))
        .toList();
  }
}

class ProfileRepository {
  ProfileRepository(this._dio);
  final Dio _dio;

  Future<Map<String, dynamic>> get() async =>
      (await _dio.get('/api/v1/profile')).data;

  Future<Map<String, dynamic>> update(Map<String, dynamic> fields) async =>
      (await _dio.put('/api/v1/profile', data: fields)).data;
}

class BarcodeRepository {
  BarcodeRepository(this._dio);
  final Dio _dio;

  Future<Map<String, dynamic>> lookup(String code) async =>
      (await _dio.get('/api/v1/barcode/$code')).data;
}

class SubscriptionRepository {
  SubscriptionRepository(this._dio);
  final Dio _dio;

  Future<void> verify(String plan, String purchaseToken) => _dio.post(
      '/api/v1/subscriptions/verify',
      data: {'plan': plan, 'purchaseToken': purchaseToken});
}

final _dioProvider = Provider((ref) => ref.watch(apiClientProvider).dio);
final mealRepositoryProvider = Provider((ref) => MealRepository(ref.watch(_dioProvider)));
final statsRepositoryProvider = Provider((ref) => StatsRepository(ref.watch(_dioProvider)));
final waterRepositoryProvider = Provider((ref) => WaterRepository(ref.watch(_dioProvider)));
final chatRepositoryProvider = Provider((ref) => ChatRepository(ref.watch(_dioProvider)));
final profileRepositoryProvider = Provider((ref) => ProfileRepository(ref.watch(_dioProvider)));
final barcodeRepositoryProvider = Provider((ref) => BarcodeRepository(ref.watch(_dioProvider)));
final subscriptionRepositoryProvider =
    Provider((ref) => SubscriptionRepository(ref.watch(_dioProvider)));
