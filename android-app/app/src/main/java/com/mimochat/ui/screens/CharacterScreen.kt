package com.mimochat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mimochat.data.Character
import com.mimochat.data.CharacterManager
import com.mimochat.data.CharacterPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(CharacterManager.getSelectedCharacter(context).id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择角色") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(CharacterPresets.characters) { character ->
                CharacterItem(
                    character = character,
                    isSelected = character.id == selectedId,
                    onClick = {
                        selectedId = character.id
                        CharacterManager.setSelectedCharacter(context, character)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun CharacterItem(character: Character, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(character.avatar, fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.titleMedium)
                Text(character.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
