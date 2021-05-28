package com.rawcopier;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.prefs.Preferences;

public class Main extends Application
{
  public static final Preferences PREF = Preferences.userRoot().node(Main.class.getName());

  @Override
  public void start(Stage primaryStage) throws Exception
  {
    Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("RawCopier.fxml")));
    primaryStage.setTitle("Raw Copier");
    primaryStage.setScene(new Scene(root, 400, 300));
    primaryStage.setResizable(false);
    primaryStage.show();
  }

  public static void main(String[] args)
  {
    launch(args);
  }
}
