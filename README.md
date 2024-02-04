# EncryptMyPack

![license](https://img.shields.io/badge/License-LGPL_2.1-blue.svg)
![version](https://img.shields.io/badge/Version-BETA-green.svg)

A command line util for encrypting and decrypting resource packs.

## Usage

Before use this tool, make sure you have java 21 runtime!

### Encryption

Command: `java -jar EncryptMyPack.jar encrypt <inputFolder> <outputFolder> [key]`

1. The key should be a 32 character long string. If you don't provide a key, the key will be `liulihaocai123456789123456789123`

2. Make sure your pack is unzipped, and in your pack directory should be a manifest.json

After the encryption, a `contents.json` should now be in your pack/output directory, and the key will be displayed in the console

### Decryption (coming soon)

Command: `java -jar EncryptMyPack.jar decrypt <inputFolder> <outputFolder> <key>`

1. Again, the key should be a 32 character long string.

## Special thanks

Thanks to [mcrputil](https://github.com/valaphee/mcrputil) for their great work!