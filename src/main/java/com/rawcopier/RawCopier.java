package com.rawcopier;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * @author Leonzio20
 */
public class RawCopier implements Initializable
{
  private final FileHandler fileHandler = new FileHandler();
  @FXML
  private GridPane pane;
  @FXML
  private Label sourceFolderLabel;
  @FXML
  private Label targetFolderLabel;
  @FXML
  private ProgressBar progressBar;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle)
  {
    fileHandler.addSourceDirectoryChangeListener((path, count) -> configure(sourceFolderLabel, path, count, "dir.source"));
    fileHandler.addTargetDirectoryChangeListener((path, count) -> configure(targetFolderLabel, path, count, "dir.target"));

    fileHandler.init(Main.PREF.get("dir.source", null), Main.PREF.get("dir.target", null));
  }

  private static void configure(Label targetFolderLabel, String path, int count, String pref)
  {
    Main.PREF.put(pref, path);
    targetFolderLabel.setText(path);
    targetFolderLabel.setTooltip(new Tooltip("Files: " + count));
  }

  public void chooseSource()
  {
    fileHandler.fillSource(choose());
  }

  private File choose()
  {
    DirectoryChooser directoryChooser = new DirectoryChooser();
    return directoryChooser.showDialog(pane.getScene().getWindow());
  }

  public void chooseTarget()
  {
    fileHandler.fillTarget(choose());
  }

  public void copy()
  {
    fileHandler.performCopy(progressBar);
  }
}
