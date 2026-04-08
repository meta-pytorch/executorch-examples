// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class SnippetManagementView : UserControl
{
    public SnippetManagementView()
    {
        InitializeComponent();
        DataContext = App.SnippetStore;
    }

    private void OnAdd(object sender, RoutedEventArgs e)
    {
        var dialog = new EditSnippetDialog { Owner = Window.GetWindow(this), Title = "Add Snippet" };
        if (dialog.ShowDialog() == true)
        {
            var entry = new Snippet
            {
                Name = dialog.NameBox.Text,
                Trigger = dialog.TriggerBox.Text,
                Content = dialog.ContentBox.Text
            };
            App.SnippetStore.AddCommand.Execute(entry);
        }
    }

    private void OnEdit(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not Snippet entry) return;
        var dialog = new EditSnippetDialog { Owner = Window.GetWindow(this), Title = "Edit Snippet" };
        dialog.NameBox.Text = entry.Name;
        dialog.TriggerBox.Text = entry.Trigger;
        dialog.ContentBox.Text = entry.Content;

        if (dialog.ShowDialog() == true)
        {
            var updated = entry.Clone();
            updated.Name = dialog.NameBox.Text;
            updated.Trigger = dialog.TriggerBox.Text;
            updated.Content = dialog.ContentBox.Text;
            App.SnippetStore.UpdateCommand.Execute(updated);
        }
    }

    private void OnDelete(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is Snippet entry)
        {
            App.SnippetStore.DeleteCommand.Execute(entry);
        }
    }
}
