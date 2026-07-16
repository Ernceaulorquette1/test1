import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// URL del backend; en emulador Android, 10.0.2.2 apunta al host.
const apiBaseUrl =
    String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');

/// Cliente HTTP con JWT automático y refresh transparente ante 401.
class ApiClient {
  ApiClient() {
    dio = Dio(BaseOptions(
      baseUrl: apiBaseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 60), // el análisis IA puede tardar
    ));
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final prefs = await SharedPreferences.getInstance();
        final token = prefs.getString('access_token');
        if (token != null && !options.path.contains('/auth/')) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401 &&
            !error.requestOptions.path.contains('/auth/')) {
          final refreshed = await _tryRefresh();
          if (refreshed) {
            final retried = await dio.fetch(error.requestOptions);
            return handler.resolve(retried);
          }
        }
        handler.next(error);
      },
    ));
  }

  late final Dio dio;

  Future<bool> _tryRefresh() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final refresh = prefs.getString('refresh_token');
      if (refresh == null) return false;
      final res = await Dio(BaseOptions(baseUrl: apiBaseUrl))
          .post('/api/v1/auth/refresh', data: {'refreshToken': refresh});
      await prefs.setString('access_token', res.data['accessToken']);
      await prefs.setString('refresh_token', res.data['refreshToken']);
      return true;
    } catch (_) {
      return false;
    }
  }
}

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient());
