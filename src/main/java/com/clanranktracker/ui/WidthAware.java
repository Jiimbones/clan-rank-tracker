package com.clanranktracker.ui;

/** A component whose height depends on the width it's given, e.g. wrapping chips. */
public interface WidthAware
{
	int heightFor(int width);
}
