# 🛑 Save Point Plugin

## Overview

The **Save Point Plugin** for IntelliJ IDEA is a tool that helps you create and manage snapshots of your project. Whether you're experimenting with new features or just want a secure fallback option, this plugin makes it easy to roll back to previous project states.

---

<!-- Plugin description -->
## Features

- **Add**: Create a new save point with a unique name and message. Frequent addition of save points can clutter your project; consider using naming conventions to keep them organized. 

- **Remove**: Delete an existing save point. This action is irreversible, so ensure the save point is no longer needed before deletion.

- **Save Points**: View all added save points in the project. Ensure save points are accurately named to avoid confusion.

- **Rollback**: Revert the project to a previously created save point. Rollback may overwrite current changes, so make sure to commit or save your work before performing this action.

- **Undo Rollback**: Revert the most recent rollback operation. This restores the project to the state before the last rollback. Use this feature cautiously to avoid unintended data loss.

- **Commit**: Save changes to the current save point. This updates the save point with new changes. Verify that the save point reflects the desired state of the project before committing.

- **Path**: Specify the path to the save point location.

- **Refresh**: Update the tool window to reflect the latest save points and changes. Refreshing ensures that all save point data is current. Use this feature to synchronize the tool window with recent updates.

<!-- Plugin description end -->
---

## 🚀 Installation

### Via IntelliJ IDEA Plugin Repository

1. Open IntelliJ IDEA.
2. Go to **Settings** > **Plugins**.
3. Search for `Save Point Plugin`.
4. Click **Install** and restart IntelliJ IDEA if prompted.

### Manual Installation

1. Download the latest file from [GET](https://plugins.jetbrains.com/plugin/25114-save-point?noRedirect=true).
2. Open IntelliJ IDEA.
3. Go to **Settings** > **Plugins**.
4. Click on the **Install Plugin from Disk** button.
5. Select the downloaded `.zip` file and click **OK**.
6. Restart IntelliJ IDEA if prompted.

---

## 🛡️ License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for more details.

---

## 💬 Support

If you encounter any issues or have questions, please open an issue on GitHub, or reach out to me via email.

---

Thank you for using the Save Point Plugin! I hope it makes managing your projects easier and more secure. 😊

---
