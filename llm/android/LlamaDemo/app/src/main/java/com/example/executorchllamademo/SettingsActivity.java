/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

  private TextView mBackendTextView;
  private TextView mModelTextView;
  private TextView mTokenizerTextView;
  private TextView mDataPathTextView;
  private TextView mModelTypeTextView;
  private EditText mSystemPromptEditText;
  private EditText mUserPromptEditText;
  private Button mLoadModelButton;
  // mSettingsFields is the single source of truth for all settings
  public SettingsFields mSettingsFields;

  // Store initial settings to detect changes
  private SettingsFields mInitialSettingsFields;

  private DemoSharedPreferences mDemoSharedPreferences;
  public static double TEMPERATURE_MIN_VALUE = 0.0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);
    if (Build.VERSION.SDK_INT >= 21) {
      getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar));
      getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.nav_bar));
    }
    ViewCompat.setOnApplyWindowInsetsListener(
        requireViewById(R.id.main),
        (v, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
          return insets;
        });
    mDemoSharedPreferences = new DemoSharedPreferences(getBaseContext());
    mSettingsFields = new SettingsFields();
    setupSettings();
  }

  private void setupSettings() {
    mBackendTextView = requireViewById(R.id.backendTextView);
    mModelTextView = requireViewById(R.id.modelTextView);
    mTokenizerTextView = requireViewById(R.id.tokenizerTextView);
    mDataPathTextView = requireViewById(R.id.dataPathTextView);
    mModelTypeTextView = requireViewById(R.id.modelTypeTextView);
    ImageButton backendImageButton = requireViewById(R.id.backendImageButton);
    ImageButton modelImageButton = requireViewById(R.id.modelImageButton);
    ImageButton tokenizerImageButton = requireViewById(R.id.tokenizerImageButton);
    ImageButton dataPathImageButton = requireViewById(R.id.dataPathImageButton);
    ImageButton modelTypeImageButton = requireViewById(R.id.modelTypeImageButton);
    mSystemPromptEditText = requireViewById(R.id.systemPromptText);
    mUserPromptEditText = requireViewById(R.id.userPromptText);
    loadSettings();

    // TODO: The two setOnClickListeners will be removed after file path issue is resolved
    backendImageButton.setOnClickListener(
        view -> {
          setupBackendSelectorDialog();
        });
    requireViewById(R.id.backendLayout)
        .setOnClickListener(
            view -> {
              setupBackendSelectorDialog();
            });

    modelImageButton.setOnClickListener(
        view -> {
          setupModelSelectorDialog();
        });
    requireViewById(R.id.modelLayout)
        .setOnClickListener(
            view -> {
              setupModelSelectorDialog();
            });

    tokenizerImageButton.setOnClickListener(
        view -> {
          setupTokenizerSelectorDialog();
        });
    requireViewById(R.id.tokenizerLayout)
        .setOnClickListener(
            view -> {
              setupTokenizerSelectorDialog();
            });

    dataPathImageButton.setOnClickListener(
        view -> {
          setupDataPathSelectorDialog();
        });
    requireViewById(R.id.dataPathLayout)
        .setOnClickListener(
            view -> {
              setupDataPathSelectorDialog();
            });

    modelTypeImageButton.setOnClickListener(
        view -> {
          setupModelTypeSelectorDialog();
        });
    requireViewById(R.id.modelTypeLayout)
        .setOnClickListener(
            view -> {
              setupModelTypeSelectorDialog();
            });
    String modelFilePath = mSettingsFields.getModelFilePath();
    if (modelFilePath != null && !modelFilePath.isEmpty()) {
      mModelTextView.setText(getFilenameFromPath(modelFilePath));
    }
    String tokenizerFilePath = mSettingsFields.getTokenizerFilePath();
    if (tokenizerFilePath != null && !tokenizerFilePath.isEmpty()) {
      mTokenizerTextView.setText(getFilenameFromPath(tokenizerFilePath));
    }
    String dataPath = mSettingsFields.getDataPath();
    if (dataPath != null && !dataPath.isEmpty()) {
      mDataPathTextView.setText(getFilenameFromPath(dataPath));
    }
    ModelType modelType = mSettingsFields.getModelType();
    ETLogging.getInstance().log("mModelType from settings " + modelType);
    if (modelType != null) {
      mModelTypeTextView.setText(modelType.toString());
    }
    BackendType backendType = mSettingsFields.getBackendType();
    ETLogging.getInstance().log("mBackendType from settings " + backendType);
    if (backendType != null) {
      mBackendTextView.setText(backendType.toString());
      setBackendSettingMode();
    }

    // Store initial values for change detection
    storeInitialSettings();

    setupParameterSettings();
    setupPromptSettings();
    setupClearChatHistoryButton();
    setupLoadModelButton();
  }

  private void setupLoadModelButton() {
    mLoadModelButton = requireViewById(R.id.loadModelButton);
    // Enable button if valid pre-filled paths are available from previous session
    updateLoadModelButtonState();
    mLoadModelButton.setOnClickListener(
        view -> {
          new AlertDialog.Builder(this)
              .setTitle("Load Model")
              .setMessage("Do you really want to load the new model?")
              .setIcon(android.R.drawable.ic_dialog_alert)
              .setPositiveButton(
                  android.R.string.yes,
                  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      // Save current UI selections to settings before loading
                      saveSettings();
                      mSettingsFields.saveLoadModelAction(true);
                      mLoadModelButton.setEnabled(false);
                      onBackPressed();
                    }
                  })
              .setNegativeButton(android.R.string.no, null)
              .show();
        });
  }

  private void setupClearChatHistoryButton() {
    Button clearChatButton = requireViewById(R.id.clearChatButton);
    clearChatButton.setOnClickListener(
        view -> {
          new AlertDialog.Builder(this)
              .setTitle("Delete Chat History")
              .setMessage("Do you really want to delete chat history?")
              .setIcon(android.R.drawable.ic_dialog_alert)
              .setPositiveButton(
                  android.R.string.yes,
                  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      mSettingsFields.saveIsClearChatHistory(true);
                    }
                  })
              .setNegativeButton(android.R.string.no, null)
              .show();
        });
  }

  private void setupParameterSettings() {
    setupTemperatureSettings();
  }

  private void setupTemperatureSettings() {
    double temperature = mSettingsFields.getTemperature();
    EditText temperatureEditText = requireViewById(R.id.temperatureEditText);
    temperatureEditText.setText(String.valueOf(temperature));
    temperatureEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            double newTemperature = Double.parseDouble(s.toString());
            mSettingsFields.saveParameters(newTemperature);
            // This is needed because temperature is changed together with model loading
            // Once temperature is no longer in LlmModule constructor, we can remove this
            mSettingsFields.saveLoadModelAction(true);
            mDemoSharedPreferences.addSettings(mSettingsFields);
          }
        });
  }

  private void setupPromptSettings() {
    setupSystemPromptSettings();
    setupUserPromptSettings();
  }

  private void setupSystemPromptSettings() {
    String systemPrompt = mSettingsFields.getSystemPrompt();
    mSystemPromptEditText.setText(systemPrompt);
    mSystemPromptEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            mSettingsFields.savePrompts(s.toString(), mSettingsFields.getUserPrompt());
          }
        });

    ImageButton resetSystemPrompt = requireViewById(R.id.resetSystemPrompt);
    resetSystemPrompt.setOnClickListener(
        view -> {
          new AlertDialog.Builder(this)
              .setTitle("Reset System Prompt")
              .setMessage("Do you really want to reset system prompt?")
              .setIcon(android.R.drawable.ic_dialog_alert)
              .setPositiveButton(
                  android.R.string.yes,
                  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      // Clear the messageAdapter and sharedPreference
                      mSystemPromptEditText.setText(PromptFormat.DEFAULT_SYSTEM_PROMPT);
                    }
                  })
              .setNegativeButton(android.R.string.no, null)
              .show();
        });
  }

  private void setupUserPromptSettings() {
    String userPrompt = mSettingsFields.getUserPrompt();
    mUserPromptEditText.setText(userPrompt);
    mUserPromptEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (isValidUserPrompt(s.toString())) {
              mSettingsFields.savePrompts(mSettingsFields.getSystemPrompt(), s.toString());
            } else {
              showInvalidPromptDialog();
            }
          }
        });

    ImageButton resetUserPrompt = requireViewById(R.id.resetUserPrompt);
    resetUserPrompt.setOnClickListener(
        view -> {
          new AlertDialog.Builder(this)
              .setTitle("Reset Prompt Template")
              .setMessage("Do you really want to reset the prompt template?")
              .setIcon(android.R.drawable.ic_dialog_alert)
              .setPositiveButton(
                  android.R.string.yes,
                  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      // Clear the messageAdapter and sharedPreference
                      mUserPromptEditText.setText(PromptFormat.getUserPromptTemplate(mSettingsFields.getModelType()));
                    }
                  })
              .setNegativeButton(android.R.string.no, null)
              .show();
        });
  }

  private boolean isValidUserPrompt(String userPrompt) {
    return userPrompt.contains(PromptFormat.USER_PLACEHOLDER);
  }

  private void showInvalidPromptDialog() {
    new AlertDialog.Builder(this)
        .setTitle("Invalid Prompt Format")
        .setMessage(
            "Prompt format must contain "
                + PromptFormat.USER_PLACEHOLDER
                + ". Do you want to reset prompt format?")
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setPositiveButton(
            android.R.string.yes,
            (dialog, whichButton) -> {
              mUserPromptEditText.setText(PromptFormat.getUserPromptTemplate(mSettingsFields.getModelType()));
            })
        .setNegativeButton(android.R.string.no, null)
        .show();
  }

  private void setupBackendSelectorDialog() {
    // Convert enum to list
    List<String> backendTypesList = new ArrayList<>();
    for (BackendType backendType : BackendType.values()) {
      backendTypesList.add(backendType.toString());
    }
    // Alert dialog builder takes in arr of string instead of list
    String[] backendTypes = backendTypesList.toArray(new String[0]);
    AlertDialog.Builder backendTypeBuilder = new AlertDialog.Builder(this);
    backendTypeBuilder.setTitle("Select backend type");
    backendTypeBuilder.setSingleChoiceItems(
        backendTypes,
        -1,
        (dialog, item) -> {
          mBackendTextView.setText(backendTypes[item]);
          mSettingsFields.saveBackendType(BackendType.valueOf(backendTypes[item]));
          setBackendSettingMode();
          updateLoadModelButtonState();
          dialog.dismiss();
        });

    backendTypeBuilder.create().show();
  }

  private void setupModelSelectorDialog() {
    String[] pteFiles = listLocalFile("/data/local/tmp/llama/", new String[] {".pte"});
    AlertDialog.Builder modelPathBuilder = new AlertDialog.Builder(this);
    modelPathBuilder.setTitle("Select model path");

    modelPathBuilder.setSingleChoiceItems(
        pteFiles,
        -1,
        (dialog, item) -> {
          mSettingsFields.saveModelPath(pteFiles[item]);
          mModelTextView.setText(getFilenameFromPath(pteFiles[item]));
          autoSelectModelType(pteFiles[item]);
          updateLoadModelButtonState();
          dialog.dismiss();
        });

    modelPathBuilder.create().show();
  }

  private void autoSelectModelType(String filePath) {
    ModelType detectedType = ModelType.fromFilePath(filePath);
    if (detectedType != null) {
      mModelType = detectedType;
      mModelTypeTextView.setText(mModelType.toString());
      mUserPromptEditText.setText(PromptFormat.getUserPromptTemplate(mModelType));
    }
  }

  private void setupDataPathSelectorDialog() {
    String[] dataPathFiles =
        listLocalFile("/data/local/tmp/llama/", new String[] {".ptd"});
    AlertDialog.Builder dataPathBuilder = new AlertDialog.Builder(this);
    dataPathBuilder.setTitle("Select data path");

    String[] dataPathOptions = new String[dataPathFiles.length + 1];
    System.arraycopy(dataPathFiles, 0, dataPathOptions, 0, dataPathFiles.length);
    dataPathOptions[dataPathOptions.length - 1] = "(unused)";

    dataPathBuilder.setSingleChoiceItems(
        dataPathOptions,
        -1,
        (dialog, item) -> {
          if (dataPathOptions[item] != "(unused)") {
            mSettingsFields.saveDataPath(dataPathOptions[item]);
            mDataPathTextView.setText(getFilenameFromPath(dataPathOptions[item]));
          } else {
            mSettingsFields.saveDataPath(null);
            mDataPathTextView.setText(getFilenameFromPath("no data path selected"));
          }
          updateLoadModelButtonState();
          dialog.dismiss();
        });

    dataPathBuilder.create().show();
  }

  private static boolean fileHasExtension(String file, String[] suffix) {
    return Arrays.stream(suffix).anyMatch(entry -> file.endsWith(entry));
  }

  static String[] listLocalFile(String path, String[] suffix) {
    File directory = new File(path);
    if (directory.exists() && directory.isDirectory()) {
      File[] files = directory.listFiles((dir, name) -> (fileHasExtension(name, suffix)));
        String[] result = new String[files.length];
      for (int i = 0; i < files.length; i++) {
        if (files[i].isFile() && fileHasExtension(files[i].getName(), suffix)) {
          result[i] = files[i].getAbsolutePath();
        }
      }
      return result;
    }
    return new String[] {};
  }

  private void setupModelTypeSelectorDialog() {
    // Convert enum to list
    List<String> modelTypesList = new ArrayList<>();
    for (ModelType modelType : ModelType.values()) {
      modelTypesList.add(modelType.toString());
    }
    // Alert dialog builder takes in arr of string instead of list
    String[] modelTypes = modelTypesList.toArray(new String[0]);
    AlertDialog.Builder modelTypeBuilder = new AlertDialog.Builder(this);
    modelTypeBuilder.setTitle("Select model type");
    modelTypeBuilder.setSingleChoiceItems(
        modelTypes,
        -1,
        (dialog, item) -> {
          mModelTypeTextView.setText(modelTypes[item]);
          ModelType selectedModelType = ModelType.valueOf(modelTypes[item]);
          mSettingsFields.saveModelType(selectedModelType);
          mUserPromptEditText.setText(PromptFormat.getUserPromptTemplate(selectedModelType));
          updateLoadModelButtonState();
          dialog.dismiss();
        });

    modelTypeBuilder.create().show();
  }

  private void setupTokenizerSelectorDialog() {
    String[] tokenizerFiles =
        listLocalFile("/data/local/tmp/llama/", new String[] {".bin", ".json", ".model"});
    AlertDialog.Builder tokenizerPathBuilder = new AlertDialog.Builder(this);
    tokenizerPathBuilder.setTitle("Select tokenizer path");
    tokenizerPathBuilder.setSingleChoiceItems(
        tokenizerFiles,
        -1,
        (dialog, item) -> {
          mSettingsFields.saveTokenizerPath(tokenizerFiles[item]);
          mTokenizerTextView.setText(getFilenameFromPath(tokenizerFiles[item]));
          updateLoadModelButtonState();
          dialog.dismiss();
        });

    tokenizerPathBuilder.create().show();
  }

  private String getFilenameFromPath(String uriFilePath) {
    if (uriFilePath == null) {
      return "";
    }
    String[] segments = uriFilePath.split("/");
    if (segments.length > 0) {
      return segments[segments.length - 1]; // get last element (aka filename)
    }
    return "";
  }

  private void storeInitialSettings() {
    mInitialSettingsFields = new SettingsFields(mSettingsFields);
  }

  private boolean hasSettingsChanged() {
    return !mSettingsFields.equals(mInitialSettingsFields);
  }

  private void updateLoadModelButtonState() {
    // Enable button if settings changed OR if valid pre-filled paths are available
    String modelFilePath = mSettingsFields.getModelFilePath();
    String tokenizerFilePath = mSettingsFields.getTokenizerFilePath();
    boolean hasValidPaths = modelFilePath != null && !modelFilePath.isEmpty()
        && tokenizerFilePath != null && !tokenizerFilePath.isEmpty();
    mLoadModelButton.setEnabled(hasSettingsChanged() || hasValidPaths);
  }

  private void setBackendSettingMode() {
    BackendType backendType = mSettingsFields.getBackendType();
    if (backendType.equals(BackendType.XNNPACK) || backendType.equals(BackendType.QUALCOMM)) {
      setXNNPACKSettingMode();
    } else if (backendType.equals(BackendType.MEDIATEK)) {
      setMediaTekSettingMode();
    }
  }

  private void setXNNPACKSettingMode() {
    requireViewById(R.id.modelLayout).setVisibility(View.VISIBLE);
    requireViewById(R.id.tokenizerLayout).setVisibility(View.VISIBLE);
    requireViewById(R.id.dataPathLayout).setVisibility(View.VISIBLE);
    requireViewById(R.id.parametersView).setVisibility(View.VISIBLE);
    requireViewById(R.id.temperatureLayout).setVisibility(View.VISIBLE);
  }

  private void setMediaTekSettingMode() {
    requireViewById(R.id.modelLayout).setVisibility(View.GONE);
    requireViewById(R.id.tokenizerLayout).setVisibility(View.GONE);
    requireViewById(R.id.dataPathLayout).setVisibility(View.GONE);
    requireViewById(R.id.parametersView).setVisibility(View.GONE);
    requireViewById(R.id.temperatureLayout).setVisibility(View.GONE);
    // For MediaTek, only set default paths if they're empty - preserve existing selections
    String modelFilePath = mSettingsFields.getModelFilePath();
    if (modelFilePath == null || modelFilePath.isEmpty()) {
      mSettingsFields.saveModelPath("/in/mtk/llama/runner");
    }
    String tokenizerFilePath = mSettingsFields.getTokenizerFilePath();
    if (tokenizerFilePath == null || tokenizerFilePath.isEmpty()) {
      mSettingsFields.saveTokenizerPath("/in/mtk/llama/runner");
    }
  }

  private void loadSettings() {
    Gson gson = new Gson();
    String settingsFieldsJSON = mDemoSharedPreferences.getSettings();
    if (!settingsFieldsJSON.isEmpty()) {
      mSettingsFields = gson.fromJson(settingsFieldsJSON, SettingsFields.class);
    }
  }

  private void saveSettings() {
    // All values are now stored directly in mSettingsFields, so just persist to SharedPreferences
    mDemoSharedPreferences.addSettings(mSettingsFields);
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    saveSettings();
  }
}
