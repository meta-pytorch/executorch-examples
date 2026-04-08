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

public partial class SnippetStoreViewModel : ObservableObject
{
    [ObservableProperty]
    private ObservableCollection<Snippet> _entries = new();

    public SnippetStoreViewModel()
    {
        Load();
    }

    [RelayCommand]
    private void Add(Snippet entry)
    {
        Entries.Add(entry);
        Save();
    }

    [RelayCommand]
    private void Update(Snippet entry)
    {
        var idx = Entries.ToList().FindIndex(e => e.Id == entry.Id);
        if (idx >= 0)
        {
            Entries[idx] = entry;
            Save();
        }
    }

    [RelayCommand]
    private void Delete(Snippet entry)
    {
        var item = Entries.FirstOrDefault(e => e.Id == entry.Id);
        if (item != null)
        {
            Entries.Remove(item);
            Save();
        }
    }

    public void MarkUsed(Guid id)
    {
        var entry = Entries.FirstOrDefault(e => e.Id == id);
        if (entry != null)
        {
            entry.LastUsedAt = DateTime.Now;
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
        PersistenceService.Save(PersistenceService.SnippetsPath, Entries.ToList());
    }

    private void Load()
    {
        var data = PersistenceService.Load<List<Snippet>>(PersistenceService.SnippetsPath);
        if (data != null && data.Count > 0)
        {
            Entries = new ObservableCollection<Snippet>(data);
            return;
        }

        // Default snippets matching macOS app
        Entries = new ObservableCollection<Snippet>(new[]
        {
            new Snippet
            {
                Name = "Daily Standup",
                Trigger = "daily standup",
                Content = "Yesterday:\n- \n\nToday:\n- \n\nBlockers:\n- None"
            },
            new Snippet
            {
                Name = "Email Signature",
                Trigger = "email signature",
                Content = "Best regards,\n[Your Name]"
            }
        });
        Save();
    }
}
