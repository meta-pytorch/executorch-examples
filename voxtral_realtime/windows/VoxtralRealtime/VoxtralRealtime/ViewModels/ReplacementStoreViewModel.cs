// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VoxtralRealtime.Models;
using VoxtralRealtime.Services;

namespace VoxtralRealtime.ViewModels;

public partial class ReplacementStoreViewModel : ObservableObject
{
    [ObservableProperty]
    private ObservableCollection<ReplacementEntry> _entries = new();

    public ReplacementStoreViewModel()
    {
        Load();
    }

    [RelayCommand]
    private void Add(ReplacementEntry entry)
    {
        Entries.Add(entry);
        Save();
    }

    [RelayCommand]
    private void Update(ReplacementEntry entry)
    {
        var idx = Entries.ToList().FindIndex(e => e.Id == entry.Id);
        if (idx >= 0)
        {
            Entries[idx] = entry;
            Save();
        }
    }

    [RelayCommand]
    private void Delete(ReplacementEntry entry)
    {
        var item = Entries.FirstOrDefault(e => e.Id == entry.Id);
        if (item != null)
        {
            Entries.Remove(item);
            Save();
        }
    }

    public void ToggleEnabled(Guid id)
    {
        var entry = Entries.FirstOrDefault(e => e.Id == id);
        if (entry != null)
        {
            entry.IsEnabled = !entry.IsEnabled;
            Save();
            OnPropertyChanged(nameof(Entries));
        }
    }

    private void Save()
    {
        PersistenceService.Save(PersistenceService.ReplacementsPath, Entries.ToList());
    }

    private void Load()
    {
        var data = PersistenceService.Load<List<ReplacementEntry>>(PersistenceService.ReplacementsPath);
        if (data != null && data.Count > 0)
        {
            Entries = new ObservableCollection<ReplacementEntry>(data);
            return;
        }

        // Default entries matching macOS app
        Entries = new ObservableCollection<ReplacementEntry>(new[]
        {
            new ReplacementEntry { Trigger = "executorch", Replacement = "ExecuTorch" },
            new ReplacementEntry { Trigger = "Executorch", Replacement = "ExecuTorch" },
            new ReplacementEntry { Trigger = "pytorch", Replacement = "PyTorch" },
            new ReplacementEntry { Trigger = "Pytorch", Replacement = "PyTorch" },
            new ReplacementEntry { Trigger = "iphone", Replacement = "iPhone" },
            new ReplacementEntry { Trigger = "github", Replacement = "GitHub" },
            new ReplacementEntry { Trigger = "Github", Replacement = "GitHub" },
            new ReplacementEntry { Trigger = "linkedin", Replacement = "LinkedIn" },
            new ReplacementEntry { Trigger = "Linkedin", Replacement = "LinkedIn" },
            new ReplacementEntry { Trigger = "chatgpt", Replacement = "ChatGPT" },
            new ReplacementEntry { Trigger = "Chatgpt", Replacement = "ChatGPT" },
            new ReplacementEntry { Trigger = "chat gpt", Replacement = "ChatGPT" },
        });
        Save();
    }
}
