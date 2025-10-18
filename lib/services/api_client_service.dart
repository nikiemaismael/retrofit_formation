import 'package:dio/dio.dart';
import 'package:retrofit/error_logger.dart';
import 'package:retrofit/http.dart';
import 'package:retrofit_test/models/post_model.dart';
part 'api_client_service.g.dart';

@RestApi(baseUrl: "https://jsonplaceholder1.typicode.com")
abstract class ApiClientService {
  factory ApiClientService(Dio dio,{String? baseUrl}) => _ApiClientService(dio,baseUrl: baseUrl);

  @GET("/posts")
  Future<List<PostModel>> getPosts();
  
  @DELETE("/posts/{id}")
  Future<void> deletePost(@Path("id") int id);

  @POST("/posts")
  Future<PostModel> createPost(@Body() PostModel post);

  @PUT("/posts/{id}")
  Future<PostModel> updatePost(@Path("id") int id, @Body() PostModel post);
}