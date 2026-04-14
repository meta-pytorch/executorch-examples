// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Windows;

namespace ParakeetApp.Views;

public partial class RenameDialog : Window
{
    public string ResultTitle { get; private set; } = "";

    public RenameDialog(string currentTitle)
    {
        InitializeComponent();
        TitleBox.Text = currentTitle;
        TitleBox.SelectAll();
        TitleBox.Focus();
    }

    private void OnSave(object sender, RoutedEventArgs e)
    {
        ResultTitle = TitleBox.Text;
        DialogResult = true;
        Close();
    }

    private void OnCancel(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }
}
