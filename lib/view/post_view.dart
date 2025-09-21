import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:retrofit_test/view_model/post_view_model.dart';

import '../models/post_model.dart';

class PostView extends StatefulWidget {
  const PostView({super.key});

  @override
  State<PostView> createState() => _PostViewState();
}

class _PostViewState extends State<PostView> {

  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _bodyController = TextEditingController();
  late PostViewModel postViewModel;

  void showForm(){
    showModalBottomSheet(
      isScrollControlled: true,
      isDismissible: true,
      context: context,
      builder: (BuildContext context) {
        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).viewInsets.bottom,
          ),
            child: SizedBox(
          //height: 400,
          width: double.infinity,
          child: SingleChildScrollView(
            child: Padding(padding: EdgeInsets.all(16),child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              spacing:8,
              children: [
                Text("Formulaire de post"),
                TextField(controller: _titleController),
                TextField(controller: _bodyController),
                SizedBox(height: 16,),
                ElevatedButton(onPressed: () {
                  final post = PostModel(
                    title: _titleController.text,
                    body: _bodyController.text, userId: 1, id: 1,
                  );
                  postViewModel.selectedPost != null ? postViewModel.updatePost(postViewModel.selectedPost!.id, post) : postViewModel.createPost(post);
                  _titleController.clear();
                  _bodyController.clear();
                  Navigator.pop(context);
                }, child: Text("Ajouter"))
              ],
            ),),
          ),
        ),
        );
      },
    );
  }
  @override
  void initState() {
    super.initState();
    postViewModel =Provider.of<PostViewModel>(context, listen: false);
    postViewModel.getPosts();
  }
  @override
  Widget build(BuildContext context) {
    return Consumer<PostViewModel>(builder: (BuildContext context, PostViewModel postViewModel, Widget? child) {
      return Scaffold(
        appBar: AppBar(
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          title: Text("Liste des posts"),
        ),
        body: Center(
          child: postViewModel.isLoadingPosts ? CircularProgressIndicator() : Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Text('Liste des posts (${postViewModel.posts.length})'),
              Expanded(
                child: ListView.builder(
                  itemCount: postViewModel.posts.length,
                  itemBuilder: (context, index) {
                    final post = postViewModel.posts[index];
                    return Card(
                      child: ListTile(
                        onTap: () {
                          _titleController.text = post.title;
                          _bodyController.text = post.body;
                          postViewModel.selectedPost = post;
                          showForm();
                        },
                        leading: Text(post.id.toString()),
                        title: Text(post.title,overflow: TextOverflow.ellipsis,),
                        subtitle: Text(post.body,overflow: TextOverflow.ellipsis,),
                        trailing: IconButton(onPressed: () {
                          showDialog(context: context, builder: (context) {
                            return AlertDialog(
                              title: Text("Supprimer le post"),
                              content: Text("Voulez-vous vraiment supprimer ce post ?"),
                              actions: [
                                TextButton(onPressed: () {
                                  Navigator.pop(context);
                                }, child: Text("Annuler"),),
                                TextButton(onPressed: () {
                                  postViewModel.deletePost(post.id);
                                  _titleController.clear();
                                  _bodyController.clear();
                                  Navigator.pop(context);
                                }, child: Text("Supprimer"),),
                              ],
                            );
                          });
                        }, icon: Icon(Icons.delete)),
                      ),
                    );
                  },
                ),
              )
            ],
          ),
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            showForm();
          },
          child: const Icon(Icons.add),
        ), // This trailing comma makes auto-formatting nicer for build methods.
      );
    },);
  }
}
