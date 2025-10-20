/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;

public class MainActivity extends AppCompatActivity implements Runnable, LlmCallback {
  private EditText mEditTextMessage;
  private ImageButton mThinkModeButton;
  private ImageButton mSendButton;
  private ImageButton mGalleryButton;
  private ImageButton mCameraButton;
  private ImageButton mAudioButton;
  private ListView mMessagesView;
  private MessageAdapter mMessageAdapter;
  private LlmModule mModule = null;
  private Message mResultMessage = null;
  private ImageButton mSettingsButton;
  private TextView mMemoryView;
  private ActivityResultLauncher<PickVisualMediaRequest> mPickGallery;
  private ActivityResultLauncher<Uri> mCameraRoll;
  private List<Uri> mSelectedImageUri;
  private ConstraintLayout mMediaPreviewConstraintLayout;
  private LinearLayout mAddMediaLayout;
  private static final int MAX_NUM_OF_IMAGES = 5;
  private static final int REQUEST_IMAGE_CAPTURE = 1;
  private Uri cameraImageUri;
  private DemoSharedPreferences mDemoSharedPreferences;
  private SettingsFields mCurrentSettingsFields;
  private Handler mMemoryUpdateHandler;
  private Runnable memoryUpdater;
  private boolean mThinkMode = false;
  private int promptID = 0;
  private Executor executor;
  private boolean sawStartHeaderId = false;
  private String mAudioFileToPrefill;
  private boolean shouldAddSystemPrompt = true;

  @Override
  public void onResult(String result) {
    if (result.equals(PromptFormat.getStopToken(mCurrentSettingsFields.getModelType()))) {
      // For gemma and llava, we need to call stop() explicitly
      if (mCurrentSettingsFields.getModelType() == ModelType.GEMMA_3
          || mCurrentSettingsFields.getModelType() == ModelType.LLAVA_1_5) {
        mModule.stop();
      }
      return;
    }
    result = PromptFormat.replaceSpecialToken(mCurrentSettingsFields.getModelType(), result);

    if (mCurrentSettingsFields.getModelType() == ModelType.LLAMA_3
        && result.equals("<|start_header_id|>")) {
      sawStartHeaderId = true;
    }
    if (mCurrentSettingsFields.getModelType() == ModelType.LLAMA_3
        && result.equals("<|end_header_id|>")) {
      sawStartHeaderId = false;
      return;
    }
    if (sawStartHeaderId) {
      return;
    }

    boolean keepResult =
        !(result.equals("\n") || result.equals("\n\n")) || !mResultMessage.getText().isEmpty();
    if (keepResult) {
      mResultMessage.appendText(result);
      run();
    }
  }

  @Override
  public void onStats(String stats) {
    runOnUiThread(
        () -> {
          if (mResultMessage != null) {
            float tps = 0;
            try {
              JSONObject jsonObject = new JSONObject(stats);
              int numGeneratedTokens = jsonObject.getInt("generated_tokens");
              int inferenceEndMs = jsonObject.getInt("inference_end_ms");
              int promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms");
              tps = (float) numGeneratedTokens / (inferenceEndMs - promptEvalEndMs) * 1000;
            } catch (JSONException e) {
              Log.e("LLM", "Error parsing JSON: " + e.getMessage());
            }
            mResultMessage.setTokensPerSecond(tps);
            mMessageAdapter.notifyDataSetChanged();
          }
        });
  }

  private void setLocalModel(
      String modelPath, String tokenizerPath, String dataPath, float temperature) {
    Message modelLoadingMessage = new Message("Loading model...", false, MessageType.SYSTEM, 0);
    ETLogging.getInstance()
        .log(
            "Loading model "
                + modelPath
                + " with tokenizer "
                + tokenizerPath
                + " data path "
                + dataPath);
    runOnUiThread(
        () -> {
          mSendButton.setEnabled(false);
          mMessageAdapter.add(modelLoadingMessage);
          mMessageAdapter.notifyDataSetChanged();
        });

    long runStartTime = System.currentTimeMillis();
    // Create LlmModule with dataPath
    if (dataPath.isEmpty()) {
      mModule =
          new LlmModule(
              ModelUtils.getModelCategory(
                  mCurrentSettingsFields.getModelType(), mCurrentSettingsFields.getBackendType()),
              modelPath,
              tokenizerPath,
              temperature);
    } else {
      mModule =
          new LlmModule(
              ModelUtils.getModelCategory(
                  mCurrentSettingsFields.getModelType(), mCurrentSettingsFields.getBackendType()),
              modelPath,
              tokenizerPath,
              temperature,
              dataPath);
    }
    int loadResult = mModule.load();
    long loadDuration = System.currentTimeMillis() - runStartTime;
    String modelLoadError = "";
    String modelInfo = "";
    if (loadResult != 0) {
      // TODO: Map the error code to a reason to let the user know why model loading failed
      modelInfo = "*Model could not load (Error Code: " + loadResult + ")*" + "\n";
      loadDuration = 0;
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle("Load failed: " + loadResult);
      runOnUiThread(
          () -> {
            AlertDialog alert = builder.create();
            alert.show();
          });
    } else {
      String[] segments = modelPath.split("/");
      String pteName = segments[segments.length - 1];
      segments = tokenizerPath.split("/");
      String tokenizerName = segments[segments.length - 1];
      modelInfo =
          "Successfully loaded model. "
              + pteName
              + " and tokenizer "
              + tokenizerName
              + " in "
              + (float) loadDuration / 1000
              + " sec."
              + " You can send text or image for inference";

      if (mCurrentSettingsFields.getModelType() == ModelType.LLAVA_1_5) {
        ETLogging.getInstance().log("Llava start prefill prompt");
        mModule.prefillPrompt(PromptFormat.getLlavaPresetPrompt());
        ETLogging.getInstance().log("Llava completes prefill prompt");
      }
    }

    Message modelLoadedMessage = new Message(modelInfo, false, MessageType.SYSTEM, 0);

    String modelLoggingInfo =
        modelLoadError
            + "Model path: "
            + modelPath
            + "\nTokenizer path: "
            + tokenizerPath
            + "\nBackend: "
            + mCurrentSettingsFields.getBackendType().toString()
            + "\nModelType: "
            + ModelUtils.getModelCategory(
                mCurrentSettingsFields.getModelType(), mCurrentSettingsFields.getBackendType())
            + "\nTemperature: "
            + temperature
            + "\nModel loaded time: "
            + loadDuration
            + " ms";
    ETLogging.getInstance().log("Load complete. " + modelLoggingInfo);

    runOnUiThread(
        () -> {
          mSendButton.setEnabled(true);
          mMessageAdapter.remove(modelLoadingMessage);
          mMessageAdapter.add(modelLoadedMessage);
          mMessageAdapter.notifyDataSetChanged();
        });
  }

  private void loadLocalModelAndParameters(
      String modelFilePath, String tokenizerFilePath, String dataPath, float temperature) {
    Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            setLocalModel(modelFilePath, tokenizerFilePath, dataPath, temperature);
          }
        };
    new Thread(runnable).start();
  }

  private void populateExistingMessages(String existingMsgJSON) {
    Gson gson = new Gson();
    Type type = new TypeToken<ArrayList<Message>>() {}.getType();
    ArrayList<Message> savedMessages = gson.fromJson(existingMsgJSON, type);
    for (Message msg : savedMessages) {
      mMessageAdapter.add(msg);
    }
    mMessageAdapter.notifyDataSetChanged();
  }

  private int setPromptID() {

    return mMessageAdapter.getMaxPromptID() + 1;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (Build.VERSION.SDK_INT >= 21) {
      getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar));
      getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.nav_bar));
    }

    try {
      Os.setenv("ADSP_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
      Os.setenv("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
    } catch (ErrnoException e) {
      finish();
    }

    mThinkModeButton = requireViewById(R.id.thinkModeButton);
    mEditTextMessage = requireViewById(R.id.editTextMessage);
    mSendButton = requireViewById(R.id.sendButton);
    mSendButton.setEnabled(false);
    mMessagesView = requireViewById(R.id.messages_view);
    mMessageAdapter = new MessageAdapter(this, R.layout.sent_message, new ArrayList<Message>());
    mMessagesView.setAdapter(mMessageAdapter);
    mDemoSharedPreferences = new DemoSharedPreferences(this.getApplicationContext());
    String existingMsgJSON = mDemoSharedPreferences.getSavedMessages();
    if (!existingMsgJSON.isEmpty()) {
      populateExistingMessages(existingMsgJSON);
      promptID = setPromptID();
    }
    mSettingsButton = requireViewById(R.id.settings);
    mSettingsButton.setOnClickListener(
        view -> {
          Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
          MainActivity.this.startActivity(myIntent);
        });

    mThinkModeButton.setOnClickListener(
        view -> {
          if (mThinkMode) {
            mThinkMode = false;
            mThinkModeButton.setImageDrawable(
                ResourcesCompat.getDrawable(
                    getResources(), R.drawable.baseline_lightbulb_24, null));
          } else {
            mThinkMode = true;
            mThinkModeButton.setImageDrawable(
                ResourcesCompat.getDrawable(getResources(), R.drawable.blue_lightbulb_24, null));
          }
          runOnUiThread(
              () -> {
                String thinkingModeText = mThinkMode ? "on" : "off";
                mMessageAdapter.add(
                    new Message(
                        "Thinking mode is " + thinkingModeText, false, MessageType.SYSTEM, 0));
                mMessageAdapter.notifyDataSetChanged();
              });
        });

    mCurrentSettingsFields = new SettingsFields();
    mMemoryUpdateHandler = new Handler(Looper.getMainLooper());
    onModelRunStopped();
    setupMediaButton();
    setupGalleryPicker();
    setupCameraRoll();
    startMemoryUpdate();
    setupShowLogsButton();
    executor = Executors.newSingleThreadExecutor();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mDemoSharedPreferences.addMessages(mMessageAdapter);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Check for if settings parameters have changed
    Gson gson = new Gson();
    String settingsFieldsJSON = mDemoSharedPreferences.getSettings();
    if (!settingsFieldsJSON.isEmpty()) {
      SettingsFields updatedSettingsFields =
          gson.fromJson(settingsFieldsJSON, SettingsFields.class);
      if (updatedSettingsFields == null) {
        // Added this check, because gson.fromJson can return null
        askUserToSelectModel();
        return;
      }
      boolean isUpdated = !mCurrentSettingsFields.equals(updatedSettingsFields);
      boolean isLoadModel = updatedSettingsFields.getIsLoadModel();
      setBackendMode(updatedSettingsFields.getBackendType());
      if (isUpdated) {
        checkForClearChatHistory(updatedSettingsFields);
        // Update current to point to the latest
        mCurrentSettingsFields = new SettingsFields(updatedSettingsFields);

        if (isLoadModel) {
          // If users change the model file, but not pressing loadModelButton, we won't load the new
          // model
          checkForUpdateAndReloadModel(updatedSettingsFields);
        } else {
          askUserToSelectModel();
        }
      }
    } else {
      askUserToSelectModel();
    }
  }

  private void setBackendMode(BackendType backendType) {
    if (backendType.equals(BackendType.XNNPACK) || backendType.equals(BackendType.QUALCOMM)) {
      setXNNPACKMode();
    } else if (backendType.equals(BackendType.MEDIATEK)) {
      setMediaTekMode();
    }
  }

  private void setXNNPACKMode() {
    requireViewById(R.id.addMediaButton).setVisibility(View.VISIBLE);
  }

  private void setMediaTekMode() {
    requireViewById(R.id.addMediaButton).setVisibility(View.GONE);
  }

  private void checkForClearChatHistory(SettingsFields updatedSettingsFields) {
    if (updatedSettingsFields.getIsClearChatHistory()) {
      mMessageAdapter.clear();
      mMessageAdapter.notifyDataSetChanged();
      mDemoSharedPreferences.removeExistingMessages();
      // changing to false since chat history has been cleared.
      updatedSettingsFields.saveIsClearChatHistory(false);
      mDemoSharedPreferences.addSettings(updatedSettingsFields);
    }
  }

  private void checkForUpdateAndReloadModel(SettingsFields updatedSettingsFields) {
    // TODO need to add 'load model' in settings and queue loading based on that
    String modelPath = updatedSettingsFields.getModelFilePath();
    String tokenizerPath = updatedSettingsFields.getTokenizerFilePath();
    String dataPath = updatedSettingsFields.getDataPath();
    double temperature = updatedSettingsFields.getTemperature();
    if (!modelPath.isEmpty() && !tokenizerPath.isEmpty()) {
      if (updatedSettingsFields.getIsLoadModel()
          || !modelPath.equals(mCurrentSettingsFields.getModelFilePath())
          || !tokenizerPath.equals(mCurrentSettingsFields.getTokenizerFilePath())
          || !dataPath.equals(mCurrentSettingsFields.getDataPath())
          || temperature != mCurrentSettingsFields.getTemperature()) {
        loadLocalModelAndParameters(
            updatedSettingsFields.getModelFilePath(),
            updatedSettingsFields.getTokenizerFilePath(),
            updatedSettingsFields.getDataPath(),
            (float) updatedSettingsFields.getTemperature());
        updatedSettingsFields.saveLoadModelAction(false);
        mDemoSharedPreferences.addSettings(updatedSettingsFields);
      }
    } else {
      askUserToSelectModel();
    }
  }

  private void askUserToSelectModel() {
    String askLoadModel =
        "To get started, select your desired model and tokenizer " + "from the top right corner";
    Message askLoadModelMessage = new Message(askLoadModel, false, MessageType.SYSTEM, 0);
    ETLogging.getInstance().log(askLoadModel);
    runOnUiThread(
        () -> {
          mMessageAdapter.add(askLoadModelMessage);
          mMessageAdapter.notifyDataSetChanged();
        });
  }

  private void setupShowLogsButton() {
    ImageButton showLogsButton = requireViewById(R.id.showLogsButton);
    showLogsButton.setOnClickListener(
        view -> {
          Intent myIntent = new Intent(MainActivity.this, LogsActivity.class);
          MainActivity.this.startActivity(myIntent);
        });
  }

  private void setupMediaButton() {
    mAddMediaLayout = requireViewById(R.id.addMediaLayout);
    mAddMediaLayout.setVisibility(View.GONE); // We hide this initially

    ImageButton addMediaButton = requireViewById(R.id.addMediaButton);
    addMediaButton.setOnClickListener(
        view -> {
          mAddMediaLayout.setVisibility(View.VISIBLE);
        });

    mGalleryButton = requireViewById(R.id.galleryButton);
    mGalleryButton.setOnClickListener(
        view -> {
          // Launch the photo picker and let the user choose only images.
          mPickGallery.launch(
              new PickVisualMediaRequest.Builder()
                  .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                  .build());
        });
    mAudioButton = requireViewById(R.id.audioButton);
    mAudioButton.setOnClickListener(
        view -> {
          mAddMediaLayout.setVisibility(View.GONE);
          String[] audioFiles =
              SettingsActivity.listLocalFile("/data/local/tmp/audio/", new String[] {".bin"});
          AlertDialog.Builder audioFilePathBuilder = new AlertDialog.Builder(this);
          audioFilePathBuilder.setTitle("Select audio feature path");
          audioFilePathBuilder.setSingleChoiceItems(
              audioFiles,
              -1,
              (dialog, item) -> {
                mAudioFileToPrefill = audioFiles[item];
                mMessageAdapter.add(
                    new Message(
                        "Selected audio: " + mAudioFileToPrefill, false, MessageType.SYSTEM, 0));
                mMessageAdapter.notifyDataSetChanged();
                dialog.dismiss();
              });
          audioFilePathBuilder.create().show();
        });
    mCameraButton = requireViewById(R.id.cameraButton);
    mCameraButton.setOnClickListener(
        view -> {
          Log.d("CameraRoll", "Check permission");
          if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
              != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[] {Manifest.permission.CAMERA},
                REQUEST_IMAGE_CAPTURE);
          } else {
            launchCamera();
          }
        });
  }

  private void setupCameraRoll() {
    // Registers a camera roll activity launcher.
    mCameraRoll =
        registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            result -> {
              if (result && cameraImageUri != null) {
                Log.d("CameraRoll", "Photo saved to uri: " + cameraImageUri);
                mAddMediaLayout.setVisibility(View.GONE);
                List<Uri> uris = new ArrayList<>();
                uris.add(cameraImageUri);
                showMediaPreview(uris);
              } else {
                // Delete the temp image file based on the url since the photo is not successfully
                // taken
                if (cameraImageUri != null) {
                  ContentResolver contentResolver = MainActivity.this.getContentResolver();
                  contentResolver.delete(cameraImageUri, null, null);
                  Log.d("CameraRoll", "No photo taken. Delete temp uri");
                }
              }
            });
    mMediaPreviewConstraintLayout = requireViewById(R.id.mediaPreviewConstraintLayout);
    ImageButton mediaPreviewCloseButton = requireViewById(R.id.mediaPreviewCloseButton);
    mediaPreviewCloseButton.setOnClickListener(
        view -> {
          mMediaPreviewConstraintLayout.setVisibility(View.GONE);
          mSelectedImageUri = null;
        });

    ImageButton addMoreImageButton = requireViewById(R.id.addMoreImageButton);
    addMoreImageButton.setOnClickListener(
        view -> {
          Log.d("addMore", "clicked");
          mMediaPreviewConstraintLayout.setVisibility(View.GONE);
          // Direct user to select type of input
          mCameraButton.callOnClick();
        });
  }

  private String updateMemoryUsage() {
    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    if (activityManager == null) {
      return "---";
    }
    activityManager.getMemoryInfo(memoryInfo);
    long totalMem = memoryInfo.totalMem / (1024 * 1024);
    long availableMem = memoryInfo.availMem / (1024 * 1024);
    long usedMem = totalMem - availableMem;
    return usedMem + "MB";
  }

  private void startMemoryUpdate() {
    mMemoryView = requireViewById(R.id.ram_usage_live);
    memoryUpdater =
        new Runnable() {
          @Override
          public void run() {
            mMemoryView.setText(updateMemoryUsage());
            mMemoryUpdateHandler.postDelayed(this, 1000);
          }
        };
    mMemoryUpdateHandler.post(memoryUpdater);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_IMAGE_CAPTURE && grantResults.length != 0) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        launchCamera();
      } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
        Log.d("CameraRoll", "Permission denied");
      }
    }
  }

  private void launchCamera() {
    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.TITLE, "New Picture");
    values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
    values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera/");
    cameraImageUri =
        MainActivity.this
            .getContentResolver()
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    mCameraRoll.launch(cameraImageUri);
  }

  private void setupGalleryPicker() {
    // Registers a photo picker activity launcher in single-select mode.
    mPickGallery =
        registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(MAX_NUM_OF_IMAGES),
            uris -> {
              if (!uris.isEmpty()) {
                Log.d("PhotoPicker", "Selected URIs: " + uris);
                mAddMediaLayout.setVisibility(View.GONE);
                for (Uri uri : uris) {
                  MainActivity.this
                      .getContentResolver()
                      .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                showMediaPreview(uris);
              } else {
                Log.d("PhotoPicker", "No media selected");
              }
            });

    mMediaPreviewConstraintLayout = requireViewById(R.id.mediaPreviewConstraintLayout);
    ImageButton mediaPreviewCloseButton = requireViewById(R.id.mediaPreviewCloseButton);
    mediaPreviewCloseButton.setOnClickListener(
        view -> {
          mMediaPreviewConstraintLayout.setVisibility(View.GONE);
          mSelectedImageUri = null;
        });

    ImageButton addMoreImageButton = requireViewById(R.id.addMoreImageButton);
    addMoreImageButton.setOnClickListener(
        view -> {
          Log.d("addMore", "clicked");
          mMediaPreviewConstraintLayout.setVisibility(View.GONE);
          // Direct user to select type of input
          mGalleryButton.callOnClick();
        });
  }

  private int getInputImageSideSize() {
    switch (mCurrentSettingsFields.getModelType()) {
      case LLAVA_1_5:
        return 336;
      case GEMMA_3:
        return 896;
      default:
        throw new IllegalArgumentException(
            "Unsupported model type: " + mCurrentSettingsFields.getModelType());
    }
  }

  private List<ETImage> getProcessedImagesForModel(List<Uri> uris) {
    List<ETImage> imageList = new ArrayList<>();
    if (uris != null) {
      uris.forEach(
          (uri) -> {
            imageList.add(new ETImage(this.getContentResolver(), uri, getInputImageSideSize()));
          });
    }
    return imageList;
  }

  private void showMediaPreview(List<Uri> uris) {
    if (mSelectedImageUri == null) {
      mSelectedImageUri = uris;
    } else {
      mSelectedImageUri.addAll(uris);
    }

    if (mSelectedImageUri.size() > MAX_NUM_OF_IMAGES) {
      mSelectedImageUri = mSelectedImageUri.subList(0, MAX_NUM_OF_IMAGES);
      Toast.makeText(
              this, "Only max " + MAX_NUM_OF_IMAGES + " images are allowed", Toast.LENGTH_SHORT)
          .show();
    }
    Log.d("mSelectedImageUri", mSelectedImageUri.size() + " " + mSelectedImageUri);

    mMediaPreviewConstraintLayout.setVisibility(View.VISIBLE);

    List<ImageView> imageViews = new ArrayList<ImageView>();

    // Pre-populate all the image views that are available from the layout (currently max 5)
    imageViews.add(requireViewById(R.id.mediaPreviewImageView1));
    imageViews.add(requireViewById(R.id.mediaPreviewImageView2));
    imageViews.add(requireViewById(R.id.mediaPreviewImageView3));
    imageViews.add(requireViewById(R.id.mediaPreviewImageView4));
    imageViews.add(requireViewById(R.id.mediaPreviewImageView5));

    // Hide all the image views (reset state)
    for (int i = 0; i < imageViews.size(); i++) {
      imageViews.get(i).setVisibility(View.GONE);
    }

    // Only show/render those that have proper Image URIs
    for (int i = 0; i < mSelectedImageUri.size(); i++) {
      imageViews.get(i).setVisibility(View.VISIBLE);
      imageViews.get(i).setImageURI(mSelectedImageUri.get(i));
    }

    // For LLava, we want to call prefill_image as soon as an image is selected
    // Llava only support 1 image for now
    if (mCurrentSettingsFields.getModelType() == ModelType.LLAVA_1_5
        || mCurrentSettingsFields.getModelType() == ModelType.GEMMA_3) {
      List<ETImage> processedImageList = getProcessedImagesForModel(mSelectedImageUri);
      if (!processedImageList.isEmpty()) {
        mMessageAdapter.add(
            new Message("Llava - Starting image Prefill.", false, MessageType.SYSTEM, 0));
        mMessageAdapter.notifyDataSetChanged();
        Runnable runnable =
            () -> {
              Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
              ETLogging.getInstance().log("Starting runnable prefill image");
              ETImage img = processedImageList.get(0);
              ETLogging.getInstance().log("Llava start prefill image");
              if (mCurrentSettingsFields.getModelType() == ModelType.LLAVA_1_5) {
                mModule.prefillImages(
                    img.getInts(),
                    img.getWidth(),
                    img.getHeight(),
                    ModelUtils.VISION_MODEL_IMAGE_CHANNELS);
              } else if (mCurrentSettingsFields.getModelType() == ModelType.GEMMA_3) {
                mModule.prefillImages(
                    img.getFloats(),
                    img.getWidth(),
                    img.getHeight(),
                    ModelUtils.VISION_MODEL_IMAGE_CHANNELS);
              }
            };
        executor.execute(runnable);
      }
    }
  }

  private void addSelectedImagesToChatThread(List<Uri> selectedImageUri) {
    if (selectedImageUri == null) {
      return;
    }
    mMediaPreviewConstraintLayout.setVisibility(View.GONE);
    for (int i = 0; i < selectedImageUri.size(); i++) {
      Uri imageURI = selectedImageUri.get(i);
      Log.d("image uri ", "test " + imageURI.getPath());
      mMessageAdapter.add(new Message(imageURI.toString(), true, MessageType.IMAGE, 0));
    }
    mMessageAdapter.notifyDataSetChanged();
  }

  private void onModelRunStarted() {
    mSendButton.setClickable(false);
    mSendButton.setImageResource(R.drawable.baseline_stop_24);
    mSendButton.setOnClickListener(
        view -> {
          mModule.stop();
        });
  }

  private void onModelRunStopped() {
    mSendButton.setClickable(true);
    mSendButton.setImageResource(R.drawable.baseline_send_24);
    mSendButton.setOnClickListener(
        view -> {
          try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
          } catch (Exception e) {
            ETLogging.getInstance().log("Keyboard dismissal error: " + e.getMessage());
          }
          addSelectedImagesToChatThread(mSelectedImageUri);
          String rawPrompt = mEditTextMessage.getText().toString();
          String finalPrompt =
              (shouldAddSystemPrompt ? mCurrentSettingsFields.getFormattedSystemPrompt() : "")
                  + mCurrentSettingsFields.getFormattedUserPrompt(rawPrompt, mThinkMode);
          shouldAddSystemPrompt = false;
          // We store raw prompt into message adapter, because we don't want to show the extra
          // tokens from system prompt
          mMessageAdapter.add(new Message(rawPrompt, true, MessageType.TEXT, promptID));
          mMessageAdapter.notifyDataSetChanged();
          mEditTextMessage.setText("");
          mResultMessage = new Message("", false, MessageType.TEXT, promptID);
          mMessageAdapter.add(mResultMessage);
          // Scroll to bottom of the list
          mMessagesView.smoothScrollToPosition(mMessageAdapter.getCount() - 1);
          // After images are added to prompt and chat thread, we clear the imageURI list
          // Note: This has to be done after imageURIs are no longer needed by LlmModule
          mSelectedImageUri = null;
          promptID++;
          Runnable runnable =
              new Runnable() {
                @Override
                public void run() {
                  Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
                  ETLogging.getInstance().log("starting runnable generate()");
                  runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          onModelRunStarted();
                        }
                      });
                  long generateStartTime = System.currentTimeMillis();
                  if (ModelUtils.getModelCategory(
                          mCurrentSettingsFields.getModelType(),
                          mCurrentSettingsFields.getBackendType())
                      == ModelUtils.VISION_MODEL) {
                    if (mCurrentSettingsFields.getModelType() == ModelType.VOXTRAL
                        && mAudioFileToPrefill != null) {
                      prefillVoxtralAudio(mAudioFileToPrefill, finalPrompt);
                      mAudioFileToPrefill = null;
                      mModule.generate(
                          "", ModelUtils.VISION_MODEL_SEQ_LEN, MainActivity.this, false);
                    } else {
                      mModule.generate(
                          finalPrompt, ModelUtils.VISION_MODEL_SEQ_LEN, MainActivity.this, false);
                    }
                  } else if (mCurrentSettingsFields.getModelType() == ModelType.LLAMA_GUARD_3) {
                    String llamaGuardPromptForClassification =
                        PromptFormat.getFormattedLlamaGuardPrompt(rawPrompt);
                    ETLogging.getInstance()
                        .log("Running inference.. prompt=" + llamaGuardPromptForClassification);
                    mModule.generate(
                        llamaGuardPromptForClassification,
                        llamaGuardPromptForClassification.length() + 64,
                        MainActivity.this,
                        false);
                  } else {
                    ETLogging.getInstance().log("Running inference.. prompt=" + finalPrompt);
                    mModule.generate(
                        finalPrompt, ModelUtils.TEXT_MODEL_SEQ_LEN, MainActivity.this, false);
                  }

                  long generateDuration = System.currentTimeMillis() - generateStartTime;
                  mResultMessage.setTotalGenerationTime(generateDuration);
                  runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          onModelRunStopped();
                        }
                      });
                  ETLogging.getInstance().log("Inference completed");
                }
              };
          executor.execute(runnable);
        });
    mMessageAdapter.notifyDataSetChanged();
  }

  private void prefillVoxtralAudio(String audioFeaturePath, String textPrompt) {
    try {
      byte[] byteData = Files.readAllBytes(Paths.get(audioFeaturePath));
      ByteBuffer buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN);
      int floatCount = byteData.length / Float.BYTES;
      float[] floats = new float[floatCount];

      // Read floats from the buffer
      for (int i = 0; i < floatCount; i++) {
        floats[i] = buffer.getFloat();
      }
      int bins = 128;
      int frames = 3000;
      int batchSize = floatCount / (bins * frames);
      mModule.prefillPrompt("<s>[INST][BEGIN_AUDIO]");
      mModule.prefillAudio(floats, batchSize, bins, frames);
      mModule.prefillPrompt(textPrompt + "[/INST]");
    } catch (IOException e) {
      Log.e("AudioPrefill", "Audio file error");
    }
  }

  @Override
  public void run() {
    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            mMessageAdapter.notifyDataSetChanged();
          }
        });
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    if (mAddMediaLayout != null && mAddMediaLayout.getVisibility() == View.VISIBLE) {
      mAddMediaLayout.setVisibility(View.GONE);
    } else {
      // Default behavior of back button
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mMemoryUpdateHandler.removeCallbacks(memoryUpdater);
    // This is to cover the case where the app is shutdown when user is on MainActivity but
    // never clicked on the logsActivity
    ETLogging.getInstance().saveLogs();
  }
}
