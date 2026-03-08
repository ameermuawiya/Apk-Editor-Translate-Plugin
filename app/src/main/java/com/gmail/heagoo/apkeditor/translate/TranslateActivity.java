package com.gmail.heagoo.apkeditor.translate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.listitem.ListItemLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * Main activity for string translation workflow.
 */
public class TranslateActivity extends AppCompatActivity implements View.OnClickListener {

    public static boolean isTaskCancelled;

    private boolean isGoogleService;
    private SharedPreferences sharedPrefs;
    private Map<String, String> googleConfigMap;
    private String userAgent;
    private String targetLanguageCode = "";
    private String targetLanguage = "";
    private String sourceLanguage = "auto";

    private String translatedListFilePath = "";
    private String untranslatedListFilePath = "";

    private RecyclerView recyclerView;
    private TranslationAdapter adapter;

    private boolean permissionFlow = false;
    private boolean isDataValid = false;

    private LinearLayout translatingLayout;
    private LinearLayout translatedLayout;
    private TextView translatingMessageTextView;
    private TextView translatedMessageTextView;
    private Button actionButton;

    private boolean isTranslationFinished = false;
    private boolean isSaved = false;

    private List<TranslateItem> translatedItems = new ArrayList<>();
    private List<TranslateItem> untranslatedItems = new ArrayList<>();

    private int totalItemsToTranslate = 0;
    private int successCount = 0;
    private int failureCount = 0;

    private List<LanguageItem> googleLangList = new ArrayList<>();
    private List<LanguageItem> yandexLangList = new ArrayList<>();

    /*
     * Model class representing a language configuration item.
     */
    private static class LanguageItem {
        final String code;
        final String name;

        /*
         * Constructor for initializing language properties.
         */
        LanguageItem(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    /*
     * Adapter for handling translation string items in the RecyclerView.
     */
    private class TranslationAdapter extends RecyclerView.Adapter<TranslationAdapter.TranslationViewHolder> {
        private final List<TranslateItem> items;

        /*
         * Constructor to initialize the adapter with data.
         */
        TranslationAdapter(List<TranslateItem> items) {
            this.items = items;
        }

        /*
         * Returns the complete list of current items.
         */
        public List<TranslateItem> getItems() {
            return items;
        }

        /*
         * Adds a newly translated item and updates the previous item to refresh its segmented shape.
         */
        public void addItem(TranslateItem item) {
            int pos = items.size();
            items.add(item);
            notifyItemInserted(pos);
            if (pos > 0) {
                notifyItemChanged(pos - 1);
            }
        }

        /*
         * Inflates the layout for a single translation item.
         */
        @NonNull
        @Override
        public TranslationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_translation_string, parent, false);
            return new TranslationViewHolder(view);
        }

        /*
         * Binds data to the view holder and sets up listeners for user interactions.
         */
        @Override
        public void onBindViewHolder(@NonNull TranslationViewHolder holder, int position) {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;

            final TranslateItem item = items.get(adapterPosition);

            holder.stringName.setText(item.name);
            holder.originValue.setText(item.originValue);

            if (holder.textWatcher != null) {
                holder.translatedValue.removeTextChangedListener(holder.textWatcher);
            }

            holder.translatedValue.setText(item.translatedValue);

            holder.textWatcher = new TextWatcher() {
                /*
                 * Invoked before text is changed.
                 */
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                /*
                 * Invoked when text is changing.
                 */
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                /*
                 * Updates the model with the new translated text.
                 */
                @Override
                public void afterTextChanged(Editable s) {
                    item.translatedValue = s.toString();
                }
            };
            holder.translatedValue.addTextChangedListener(holder.textWatcher);

            ((ListItemLayout) holder.itemView).updateAppearance(adapterPosition, getItemCount());

            holder.btnReset.setOnClickListener(v -> {
                holder.translatedValue.setText("");
                holder.translatedValue.requestFocus();
            });

            holder.btnRetry.setOnClickListener(v -> {
                isTaskCancelled = false;
                if (isResourceOrFormat(item.originValue)) {
                    holder.translatedValue.setText(item.originValue);
                    return;
                }

                showToast(getString(R.string.translating));
                holder.btnRetry.setEnabled(false);

                TranslationEngine.executeTranslation(
                        TranslateActivity.this,
                        isGoogleService,
                        item.originValue,
                        sourceLanguage,
                        targetLanguage,
                        userAgent,
                        googleConfigMap,
                        new TranslationEngine.TranslationCallback() {
                            /*
                             * Applies formatting and sets the text on translation success.
                             */
                            @Override
                            public void onSuccess(String result) {
                                String formatted = formatTranslatedString(result);
                                holder.translatedValue.setText(formatted);
                                holder.btnRetry.setEnabled(true);
                            }

                            /*
                             * Displays the error message on translation failure.
                             */
                            @Override
                            public void onError(String errorMsg) {
                                showToast(errorMsg);
                                holder.btnRetry.setEnabled(true);
                            }
                        });
            });
        }

        /*
         * Returns the total count of items in the list.
         */
        @Override
        public int getItemCount() {
            return items.size();
        }

        /*
         * ViewHolder class for holding translation view elements.
         */
        class TranslationViewHolder extends RecyclerView.ViewHolder {
            TextView stringName;
            TextView originValue;
            TextInputEditText translatedValue;
            MaterialButton btnRetry;
            MaterialButton btnReset;
            TextWatcher textWatcher;

            /*
             * Constructor that finds and assigns the views from the layout.
             */
            TranslationViewHolder(@NonNull View itemView) {
                super(itemView);
                stringName = itemView.findViewById(R.id.string_name);
                originValue = itemView.findViewById(R.id.origin_value);
                translatedValue = itemView.findViewById(R.id.translated_value);
                btnRetry = itemView.findViewById(R.id.btn_retry_translate);
                btnReset = itemView.findViewById(R.id.btn_reset_field);
            }
        }
    }

    /*
     * Initializes activity state, reads intents, and starts the permission workflow.
     */
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent intent = getIntent();
        Bundle extras = (intent != null) ? intent.getExtras() : null;

        if (extras != null
                && extras.containsKey("targetLanguageCode")
                && extras.containsKey("translatedList_file")) {
            this.targetLanguageCode = extras.getString("targetLanguageCode");
            this.targetLanguage = formatLanguageCode();
            this.translatedListFilePath = extras.getString("translatedList_file");
            this.untranslatedListFilePath = extras.getString("untranslatedList_file");
            this.isDataValid = true;
        } else {
            this.isDataValid = false;
            Log.e("TranslateActivity", "No data received. Application opened without required intent extras.");
        }

        isTaskCancelled = false;
        this.googleConfigMap = new HashMap<>();
        this.userAgent = new WebView(this).getSettings().getUserAgentString();
        this.sharedPrefs = getSharedPreferences("apkeditor_translate_prefs", Context.MODE_PRIVATE);

        this.isGoogleService = this.sharedPrefs.getInt("service", 0) == 0;

        setContentView(R.layout.activity_translate);
        loadLanguageLists();

        checkStoragePermission();
    }

    /*
     * Resumes the activity and verifies if the permission flow has been completed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (permissionFlow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                permissionFlow = false;
                if (!this.isTranslationFinished && !this.isSaved) {
                    startInitialization();
                }
            }
        }
    }

    /*
     * Saves file paths and language codes to survive orientation changes.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        bundle.putString("targetLanguageCode", this.targetLanguageCode);
        bundle.putString("translatedList_file", this.translatedListFilePath);
        bundle.putString("untranslatedList_file", this.untranslatedListFilePath);
        super.onSaveInstanceState(bundle);
    }

    /*
     * Restores saved file paths and language codes after orientation changes.
     */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        this.targetLanguageCode = bundle.getString("targetLanguageCode");
        this.targetLanguage = formatLanguageCode();
        this.translatedListFilePath = bundle.getString("translatedList_file");
        this.untranslatedListFilePath = bundle.getString("untranslatedList_file");
    }

    /*
     * Handles the callback for legacy permission requests.
     */
    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        if (code == 1010) {
            boolean granted = true;
            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startInitialization();
            } else {
                showToast(getString(R.string.permissions));
            }
            return;
        }
        super.onRequestPermissionsResult(code, perms, results);
    }

    /*
     * Click listener for handling UI button actions globally.
     */
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_stop_or_save) {
            if (this.isTranslationFinished) {
                extractAndSaveTranslations();
                this.isSaved = true;
                finish();
            } else {
                triggerStopProcess();
            }
        }
    }

    /*
     * Verifies storage permission and requests it if not granted based on Android version.
     */
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                permissionFlow = true;
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
            startInitialization();
        } else {
            requestLegacyPermissions(this, new String[]{"READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"});
        }
    }

    /*
     * Requests multiple storage permissions for legacy Android devices.
     */
    private void requestLegacyPermissions(Activity activity, String[] strArr) {
        ArrayList<String> list = new ArrayList<>();
        for (String perm : strArr) {
            if (activity.checkSelfPermission("android.permission." + perm) != PackageManager.PERMISSION_GRANTED) {
                list.add("android.permission." + perm);
            }
        }
        if (list.isEmpty()) {
            startInitialization();
        } else {
            activity.requestPermissions(list.toArray(new String[0]), 1010);
        }
    }

    /*
     * Starts data loading and shows dialog after successful permission checks.
     */
    private void startInitialization() {
        if (isDataValid) {
            loadSerializedData();
            populateInitialItems(this.translatedItems);
            showConfigurationDialog();
        } else {
            populateInitialItems(new ArrayList<>());
            showToast(getString(R.string.no_strings_to_translate));
        }
    }

    /*
     * Reads translated and untranslated list items from the local storage files.
     */
    private void loadSerializedData() {
        this.translatedItems = readObjectFromFile(this.translatedListFilePath);
        this.untranslatedItems = readObjectFromFile(this.untranslatedListFilePath);
    }

    /*
     * Configures the layout and adapter for the main RecyclerView list.
     */
    private void populateInitialItems(List<TranslateItem> list) {
        this.recyclerView = findViewById(R.id.recycler_view);
        this.translatingLayout = findViewById(R.id.translating_layout);
        this.translatedLayout = findViewById(R.id.translated_layout);
        this.translatingMessageTextView = findViewById(R.id.translating_msg);
        this.translatedMessageTextView = findViewById(R.id.translated_msg);

        List<TranslateItem> initialList = (list != null) ? list : new ArrayList<>();
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.adapter = new TranslationAdapter(initialList);
        this.recyclerView.setAdapter(this.adapter);

        this.actionButton = findViewById(R.id.btn_stop_or_save);
        this.actionButton.setOnClickListener(this);
    }

    /*
     * Creates and displays the configuration dialog for selecting engine and languages.
     */
    private void showConfigurationDialog() {
        if (googleLangList.isEmpty()) loadLanguageLists();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_translation_settings, null, false);

        final Spinner spinnerService = dialogView.findViewById(R.id.startdialogSpinner1);
        final Spinner spinnerSourceLang = dialogView.findViewById(R.id.startdialogSpinner2);
        final Spinner spinnerTargetLang = dialogView.findViewById(R.id.startdialogSpinner3);

        spinnerService.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.services)));
        spinnerService.setSelection(this.isGoogleService ? 0 : 1);

        updateLanguageSpinners(spinnerSourceLang, spinnerTargetLang, this.isGoogleService);

        spinnerService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            /*
             * Updates selected translation engine and re-populates language options.
             */
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sharedPrefs.edit().putInt("service", position).apply();
                isGoogleService = (position == 0);
                updateLanguageSpinners(spinnerSourceLang, spinnerTargetLang, isGoogleService);
            }

            /*
             * Handles scenario where no engine is selected.
             */
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setCancelable(false);
        builder.setView(dialogView);

        builder.setPositiveButton(R.string.start, (dialog, which) -> {
            List<LanguageItem> currentList = isGoogleService ? googleLangList : yandexLangList;
            sourceLanguage = currentList.get(spinnerSourceLang.getSelectedItemPosition()).code;
            targetLanguage = currentList.get(spinnerTargetLang.getSelectedItemPosition()).code;

            if (targetLanguage.contains("zh")) {
                targetLanguageCode = "-zh-rCN";
            } else {
                targetLanguageCode = "-" + targetLanguage;
            }
            prepareAndStartTranslation();
            dialog.dismiss();
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> finish());
        builder.show();
    }

    /*
     * Switches adapter items for spinners based on the selected translation engine.
     */
    private void updateLanguageSpinners(Spinner source, Spinner target, boolean isGoogle) {
        List<LanguageItem> list = isGoogle ? googleLangList : yandexLangList;
        List<String> names = new ArrayList<>();
        for (LanguageItem item : list) names.add(item.name);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names);

        source.setAdapter(spinnerAdapter);
        target.setAdapter(spinnerAdapter);

        String code = formatLanguageCode();
        int index = findLanguageIndex(list, code);
        if (index >= 0) target.setSelection(index);
    }

    /*
     * Retrieves parsing formats from arrays and builds the available language lists.
     */
    private void loadLanguageLists() {
        googleLangList = parseLanguages(R.array.google_languages);
        yandexLangList = parseLanguages(R.array.yandex_languages);
    }

    /*
     * Transforms resource arrays into distinct language models based on delimiter.
     */
    private List<LanguageItem> parseLanguages(int resId) {
        String[] data = getResources().getStringArray(resId);
        List<LanguageItem> list = new ArrayList<>();
        for (String s : data) {
            String[] parts = s.split("\\|", 2);
            if (parts.length == 2) {
                list.add(new LanguageItem(parts[0], parts[1]));
            }
        }
        return list;
    }

    /*
     * Locates the array index for a given language code.
     */
    private int findLanguageIndex(List<LanguageItem> list, String code) {
        if (code == null) return 1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).code.equalsIgnoreCase(code)) return i;
        }
        return 1;
    }

    /*
     * Reformats code rules and transforms it back to standard locale casing.
     */
    private String formatLanguageCode() {
        if (targetLanguageCode == null || targetLanguageCode.isEmpty()) return "en";
        String substring = targetLanguageCode.startsWith("-")
                ? targetLanguageCode.substring(1)
                : targetLanguageCode;
        int indexOf = substring.indexOf('-');
        if (indexOf != -1) {
            substring = substring.substring(0, indexOf + 1) + substring.substring(indexOf + 2);
        }
        return substring.toLowerCase(Locale.ROOT);
    }

    /*
     * Resets counters, prepares views, and executes batch translation logic.
     */
    private void prepareAndStartTranslation() {
        this.translatingMessageTextView.setText(R.string.translating);
        this.translatingLayout.setVisibility(View.VISIBLE);
        this.translatedLayout.setVisibility(View.GONE);
        this.actionButton.setText(R.string.stop);
        this.isSaved = false;
        this.isTranslationFinished = false;
        this.totalItemsToTranslate = this.untranslatedItems != null ? this.untranslatedItems.size() : 0;
        this.successCount = 0;
        this.failureCount = 0;

        if (this.totalItemsToTranslate > 0) {
            startBatchTranslation();
        } else {
            showCompletionSummary();
        }
    }

    /*
     * Prepares configuration and iterates list to execute background translations.
     */
    private void startBatchTranslation() {
        isTaskCancelled = false;
        boolean bypass = !this.isGoogleService;

        TranslationEngine.fetchGoogleConfig(
                this.userAgent,
                bypass,
                new TranslationEngine.ConfigCallback() {
                    /*
                     * Runs list processing once Google configuration is fully gathered.
                     */
                    @Override
                    public void onConfigSuccess(Map<String, String> config) {
                        if (googleConfigMap.isEmpty() && !config.isEmpty()) {
                            googleConfigMap.putAll(config);
                        }
                        if (untranslatedItems == null) return;

                        for (TranslateItem item : untranslatedItems) {
                            if (isTaskCancelled) {
                                triggerStopProcess();
                                return;
                            } else if (isResourceOrFormat(item.originValue)) {
                                item.translatedValue = item.originValue;
                                addTranslatedItem(item);
                            } else {
                                TranslationEngine.executeTranslation(
                                        TranslateActivity.this,
                                        isGoogleService,
                                        item.originValue,
                                        sourceLanguage,
                                        targetLanguage,
                                        userAgent,
                                        googleConfigMap,
                                        new TranslationEngine.TranslationCallback() {
                                            /*
                                             * Handles successful translation output formatting and insertion.
                                             */
                                            @Override
                                            public void onSuccess(String result) {
                                                item.translatedValue = formatTranslatedString(result);
                                                addTranslatedItem(item);
                                            }

                                            /*
                                             * Handles failed translation by sending empty or original values.
                                             */
                                            @Override
                                            public void onError(String errorMsg) {
                                                addTranslatedItem(item);
                                            }
                                        });
                            }
                        }
                    }

                    /*
                     * Triggers process stop due to failure in fetching network configuration.
                     */
                    @Override
                    public void onConfigError(String errorMsg) {
                        showToast(getString(R.string.error));
                        triggerStopProcess();
                    }
                });
    }

    /*
     * Adds translated record into adapter and dynamically updates UI count status.
     */
    private void addTranslatedItem(TranslateItem translateItem) {
        this.adapter.addItem(translateItem);
        if (translateItem.translatedValue != null) {
            this.successCount++;
        } else {
            this.failureCount++;
        }

        String string = getString(R.string.translated);
        int remaining = this.totalItemsToTranslate - this.failureCount;
        this.translatingMessageTextView.setText(
                String.format(Locale.getDefault(), "%d / %d %s", this.successCount, remaining, string));

        if (this.successCount >= remaining) {
            triggerStopProcess();
        }
    }

    /*
     * Computes the final text strings for translated counts and sets UI end states.
     */
    private void showCompletionSummary() {
        this.isTranslationFinished = true;
        this.translatingLayout.setVisibility(View.GONE);
        this.translatedLayout.setVisibility(View.VISIBLE);

        String format;
        if (this.totalItemsToTranslate == 0) {
            format = getString(R.string.error_no_string_totranslate);
        } else {
            format = String.format(Locale.getDefault(), getString(R.string.translated_format), this.successCount);
        }

        if (this.failureCount > 0) {
            format += ", " + String.format(Locale.getDefault(), getString(R.string.failed_format), this.failureCount);
        }

        int untranslatedCount = (this.totalItemsToTranslate - this.successCount) - this.failureCount;
        if (untranslatedCount > 0) {
            format += ", " + String.format(Locale.getDefault(), getString(R.string.untranslated_format), untranslatedCount);
        }

        this.translatedMessageTextView.setText(format);
        this.actionButton.setText(this.successCount > 0 ? R.string.save_and_close : R.string.close);
    }

    /*
     * Halts internal engine execution flows and forces final completion display.
     */
    private void triggerStopProcess() {
        isTaskCancelled = true;
        showCompletionSummary();
    }

    /*
     * Grabs completed adapter rows and initiates final saving process for valid entries.
     */
    private void extractAndSaveTranslations() {
        ArrayList<TranslateItem> arrayList = new ArrayList<>();
        List<TranslateItem> adapterItems = this.adapter.getItems();

        for (TranslateItem item : adapterItems) {
            if (item.translatedValue != null && !item.translatedValue.trim().isEmpty()) {
                arrayList.add(new TranslateItem(item.name, null, item.translatedValue));
            }
        }

        if (!arrayList.isEmpty()) {
            saveTranslationsAndFinish(arrayList);
        }
    }

    /*
     * Executes list serialization and returns intent bundle to original sender.
     */
    private void saveTranslationsAndFinish(List<TranslateItem> list) {
        if (isDataValid) {
            writeObjectToFile(this.translatedListFilePath, list);
            Intent intent = new Intent();
            intent.putExtra("targetLanguageCode", this.targetLanguageCode);
            intent.putExtra("translatedList_file", this.translatedListFilePath);
            setResult(RESULT_OK, intent);
        }
    }

    /*
     * Outputs basic string text to application context via a Toast block.
     */
    private void showToast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    /*
     * Creates stream objects and securely reads data blocks from internal paths.
     */
    @SuppressWarnings("unchecked")
    private List<TranslateItem> readObjectFromFile(String str) {
        if (str == null || str.isEmpty()) return new ArrayList<>();
        ObjectInputStream objectInputStream = null;
        try {
            objectInputStream = new ObjectInputStream(new FileInputStream(new File(str)));
            return (List<TranslateItem>) objectInputStream.readObject();
        } catch (Exception e) {
            Log.e("TranslateActivity", "File Read Error", e);
            return new ArrayList<>();
        } finally {
            closeResource(objectInputStream);
        }
    }

    /*
     * Opens write channels and passes serialized blocks back to internal paths.
     */
    private void writeObjectToFile(String str, Object obj) {
        if (str == null || str.isEmpty()) return;
        ObjectOutputStream objectOutputStream = null;
        try {
            objectOutputStream = new ObjectOutputStream(new FileOutputStream(new File(str)));
            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
        } catch (Exception e) {
            Log.e("TranslateActivity", "File Write Error", e);
        } finally {
            closeResource(objectOutputStream);
        }
    }

    /*
     * Forcefully dismisses open streams and catches generic fallback exceptions.
     */
    private void closeResource(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {}
        }
    }

    /*
     * Analyzes raw translation blocks and repairs standard Android string arguments.
     */
    private String formatTranslatedString(String str) {
        if (str == null) return null;

        String replaceAll =
                str.replaceAll("\\\\\\s{1,2}\"", "\\\\\"").replaceAll("\\\\\\s{1,2}'", "\\\\'");

        if (replaceAll.contains("%") || replaceAll.contains("$")) {
            return replaceAll
                    .replaceAll("%\\s{0,2}(\\d)\\s{0,2}\\$\\s{0,2}([dDsSдДсС])", "%$1\\$$2")
                    .replaceAll("%\\s{0,2}(\\d)\\s{0,2}\\$\\s{0,2}(\\d+)([dDsSдДсС])", "%$1\\$$2$3")
                    .replaceAll("%\\s{0,2}(\\d|\\w)", "%$1")
                    .replaceAll("%\\s{0,2}([dDдД])", "%d")
                    .replaceAll("%\\s{0,2}([sSсС])", "%s")
                    .replaceAll("\\$\\s{0,2}([dDдД])", "\\$d")
                    .replaceAll("\\$\\s{0,2}(\\d+)([dDдД])", "\\$$1d")
                    .replaceAll("\\$\\s{0,2}([sSсС])", "\\$s")
                    .replaceAll("%\\s{0,2}(\\d)\\$(ов|ы)", "%$1\\$s")
                    .replaceAll("%(\\d)\\$s\\sв$", "%$1\\$s")
                    .replaceAll("([a-zA-Zа-яА-Я:/.,])%(\\d|\\w)", "$1 %$2")
                    .replaceAll("%d\\s{1,2}%%", "%d%% ");
        }
        return replaceAll.contains("...") ? replaceAll.replaceAll("\\s\\.{3}$", "...") : replaceAll;
    }

    /*
     * Prevents system string blocks and core XML commands from undergoing translation.
     */
    private boolean isResourceOrFormat(String str) {
        String[] resources = {
            "string", "color", "dimen", "attr", "style", "drawable", "anim", "layout", "raw", "xml"
        };
        for (String res : resources) {
            if (str.startsWith("@android:" + res + "/") || str.startsWith("@" + res + "/")) {
                return true;
            }
        }

        String[] formats = {
            "",
            "true",
            "false",
            "null",
            "%b %-e, %Y, %-l:%M:%S %p",
            "%1$d P",
            "MMMM d",
            "ccc, dd MMM yyyy",
            "MM/dd HH:mm:ss",
            "yy/MM/dd HH:mm:ss",
            "sans-serif",
            "sans-serif-light",
            "sans-serif-condensed",
            "sans-serif-black",
            "sans-serif-thin",
            "sans-serif-medium"
        };
        for (String format : formats) {
            if (str.equals(format)) {
                return true;
            }
        }
        return false;
    }
}