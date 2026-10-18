# MLBB Region Filter

Android application for testing regional IP filtering for Mobile Legends.

## Features

- Select allowed countries
- Start Android VPN
- Stop VPN
- Future IP-based country filtering
- Future MLBB-only traffic filtering

## Architecture

The application uses Android `VpnService`.

Traffic flow:

Mobile Legends
        |
        v
Android VPN
        |
        v
IP filtering
        |
        +---- ALLOW ----> Internet
        |
        +---- DROP -----> Blocked

## Important

The current version is a VPN prototype.

It does not yet contain:

- IP-to-country database
- TCP forwarding
- UDP forwarding
- DNS forwarding
- automatic MLBB server detection

These components will be implemented separately.

## Target

The final application should allow the user to select countries and restrict MLBB game-server traffic to IP ranges belonging to those countries.
