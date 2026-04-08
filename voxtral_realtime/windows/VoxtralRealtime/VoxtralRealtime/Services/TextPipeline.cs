// Copyright (c) Meta Platforms, Inc. and affiliates.
// All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

using System.Text.RegularExpressions;
using VoxtralRealtime.Models;
using VoxtralRealtime.ViewModels;

namespace VoxtralRealtime.Services;

public enum TextContext { SessionSave, Dictation }

public class TextPipeline
{
    private readonly ReplacementStoreViewModel _replacementStore;
    private readonly SnippetStoreViewModel _snippetStore;

    public TextPipeline(
        ReplacementStoreViewModel replacementStore,
        SnippetStoreViewModel snippetStore)
    {
        _replacementStore = replacementStore;
        _snippetStore = snippetStore;
    }

    public TextProcessingResult Process(string text, TextContext context)
    {
        if (string.IsNullOrWhiteSpace(text))
            return new TextProcessingResult(text, text, new(), new());

        var rawText = text;
        bool skipSnippets = false;

        if (text.StartsWith("literal ", StringComparison.OrdinalIgnoreCase))
        {
            text = text["literal ".Length..];
            skipSnippets = true;
        }

        var tags = new List<string>();
        var usedSnippetIds = new List<Guid>();

        // Apply snippet expansion (dictation context only, full text match)
        if (context == TextContext.Dictation && !skipSnippets)
        {
            var normalized = NormalizeForMatch(text);
            foreach (var snippet in _snippetStore.Entries.Where(s => s.IsEnabled))
            {
                if (NormalizeForMatch(snippet.Trigger) == normalized)
                {
                    text = snippet.Content;
                    usedSnippetIds.Add(snippet.Id);
                    _snippetStore.MarkUsed(snippet.Id);
                    break;
                }
            }
        }

        // Apply replacements (sorted by trigger length descending for longest-match-first)
        var replacements = _replacementStore.Entries
            .Where(r => r.IsEnabled)
            .OrderByDescending(r => r.Trigger.Length)
            .ToList();

        foreach (var entry in replacements)
        {
            var pattern = entry.RequiresWordBoundary
                ? $@"\b{Regex.Escape(entry.Trigger)}\b"
                : Regex.Escape(entry.Trigger);

            var options = entry.IsCaseSensitive
                ? RegexOptions.None
                : RegexOptions.IgnoreCase;

            text = Regex.Replace(text, pattern, match =>
            {
                return PreserveCase(match.Value, entry.Replacement);
            }, options);
        }

        return new TextProcessingResult(rawText, text, tags, usedSnippetIds, skipSnippets);
    }

    public string NormalizeForWakePhrase(string text)
    {
        return NormalizeForMatch(text);
    }

    private static string NormalizeForMatch(string text)
    {
        // Strip punctuation, lowercase, collapse whitespace
        var cleaned = Regex.Replace(text.Trim(), @"[^\w\s]", "");
        cleaned = Regex.Replace(cleaned, @"\s+", " ");
        return cleaned.ToLowerInvariant().Trim();
    }

    private static string PreserveCase(string original, string replacement)
    {
        if (original == original.ToUpperInvariant())
            return replacement.ToUpperInvariant();
        if (original.Length > 0 && char.IsUpper(original[0]))
            return char.ToUpper(replacement[0]) + replacement[1..];
        return replacement;
    }
}
