# EncryptMyPack

![license](https://img.shields.io/badge/License-LGPL_3.0-blue.svg)
![version](https://img.shields.io/badge/Version-1.0.0-green.svg)

A command line util for encrypting and decrypting resource packs.

## Feature
 - Encrypt and decrypt resource packs
 - Support resource pack that contains sub packs

## Usage

Download the latest executable file from [release](https://github.com/AllayMC/EncryptMyPack/releases/latest)

### Encryption

Command: `EncryptMyPack.exe encrypt <inputZip> <outputZip> [key]`

1. The key should be a 32 character long string. If you don't provide a key, the key will be `liulihaocai123456789123456789123`

2. Make sure your pack is a zip file, and in your pack should be a manifest.json

After the encryption, a `contents.json` should now be in output zip file, and the key will be displayed in the console

### Decryption

Command: `EncryptMyPack.exe decrypt <inputZip> <outputZip> <key>`

1. Again, the key should be a 32 character long string.
2. To decrypt the pack, you must provide its key

## Build

This project use graalvm native image to transform the jar to a native executable

So you should set up graalvm and set project JDK to graalvm before building

## Special thanks

Thanks to [mcrputil](https://github.com/valaphee/mcrputil) for their great work!

## License
LGPL-3.0 © AllayMC
