// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Windows;
using System.Windows.Controls;
using VoxtralRealtime.Models;

namespace VoxtralRealtime.Views;

public partial class ReplacementManagementView : UserControl
{
    public ReplacementManagementView()
    {
        InitializeComponent();
        DataContext = App.ReplacementStore;
    }

    private void OnAdd(object sender, RoutedEventArgs e)
    {
        var dialog = new EditReplacementDialog { Owner = Window.GetWindow(this), Title = "Add Replacement" };
        if (dialog.ShowDialog() == true)
        {
            var entry = new ReplacementEntry
            {
                Trigger = dialog.TriggerBox.Text,
                Replacement = dialog.ReplacementBox.Text,
                IsCaseSensitive = dialog.CaseSensitiveCheck.IsChecked == true,
                RequiresWordBoundary = dialog.WordBoundaryCheck.IsChecked == true
            };
            App.ReplacementStore.AddCommand.Execute(entry);
        }
    }

    private void OnEdit(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is not ReplacementEntry entry) return;
        var dialog = new EditReplacementDialog { Owner = Window.GetWindow(this), Title = "Edit Replacement" };
        dialog.TriggerBox.Text = entry.Trigger;
        dialog.ReplacementBox.Text = entry.Replacement;
        dialog.CaseSensitiveCheck.IsChecked = entry.IsCaseSensitive;
        dialog.WordBoundaryCheck.IsChecked = entry.RequiresWordBoundary;

        if (dialog.ShowDialog() == true)
        {
            var updated = entry.Clone();
            updated.Trigger = dialog.TriggerBox.Text;
            updated.Replacement = dialog.ReplacementBox.Text;
            updated.IsCaseSensitive = dialog.CaseSensitiveCheck.IsChecked == true;
            updated.RequiresWordBoundary = dialog.WordBoundaryCheck.IsChecked == true;
            App.ReplacementStore.UpdateCommand.Execute(updated);
        }
    }

    private void OnDelete(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is ReplacementEntry entry)
        {
            App.ReplacementStore.DeleteCommand.Execute(entry);
        }
    }

    private void OnToggleEnabled(object sender, RoutedEventArgs e)
    {
        if ((sender as CheckBox)?.DataContext is ReplacementEntry entry)
        {
            // CheckBox binding already toggled the value; trigger a save
            App.ReplacementStore.ToggleEnabled(entry.Id);
        }
    }
}
