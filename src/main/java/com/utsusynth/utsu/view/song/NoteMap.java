package com.utsusynth.utsu.view.song;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.google.inject.Inject;
import com.utsusynth.utsu.common.RegionBounds;
import com.utsusynth.utsu.common.data.EnvelopeData;
import com.utsusynth.utsu.common.data.PitchbendData;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;
import com.utsusynth.utsu.view.song.note.Note;
import com.utsusynth.utsu.view.song.note.envelope.Envelope;
import com.utsusynth.utsu.view.song.note.envelope.EnvelopeCallback;
import com.utsusynth.utsu.view.song.note.envelope.EnvelopeFactory;
import com.utsusynth.utsu.view.song.note.portamento.Portamento;
import com.utsusynth.utsu.view.song.note.portamento.PortamentoCallback;
import com.utsusynth.utsu.view.song.note.portamento.PortamentoFactory;
import javafx.scene.Group;

public class NoteMap {
    private final EnvelopeFactory envelopeFactory;
    private final PortamentoFactory portamentoFactory;

    // Maps absolute position (in ms) to track note's data.
    private Map<Integer, Note> noteMap;
    private Map<Integer, Envelope> envelopeMap;
    private Map<Integer, Portamento> portamentoMap;
    private Set<Note> allNotes; // Includes invalid notes.

    private RegionBounds visibleRegion;
    private Group visibleNotes; // Only includes notes in the visible region.
    private Group visibleEnvelopes; // Only includes envelopes in the visible region.
    private Group visiblePitchbends; // Only includes pitchbends in the visible region.

    @Inject
    public NoteMap(EnvelopeFactory envFactory, PortamentoFactory portFactory) {
        this.envelopeFactory = envFactory;
        this.portamentoFactory = portFactory;
        clear();
    }

    Group getNotesElement() {
        return visibleNotes;
    }

    Group getEnvelopesElement() {
        return visibleEnvelopes;
    }

    Group getPitchbendsElement() {
        return visiblePitchbends;
    }

    void clear() {
        noteMap = new HashMap<>();
        envelopeMap = new HashMap<>();
        portamentoMap = new HashMap<>();
        allNotes = new HashSet<>();
        visibleRegion = RegionBounds.WHOLE_SONG;
        visibleNotes = new Group();
        visibleEnvelopes = new Group();
        visiblePitchbends = new Group();
    }

    void setVisibleRegion(RegionBounds newRegion) {
        if (newRegion.getMinMs() == visibleRegion.getMinMs()
                && newRegion.getMaxMs() == visibleRegion.getMaxMs()) {
            // Do nothing if region does not change.
            return;
        }
        visibleRegion = newRegion;

        // Add all elements from new visibleRegion.
        for (Note note : allNotes) {
            int pos = note.getAbsPositionMs();
            if (newRegion.intersects(note.getBounds())) {
                if (!visibleNotes.getChildren().contains(note.getElement())) {
                    visibleNotes.getChildren().add(note.getElement());
                    if (envelopeMap.containsKey(pos) && portamentoMap.containsKey(pos)) {
                        Envelope envelope = envelopeMap.get(pos);
                        if (!visibleEnvelopes.getChildren().contains(envelope.getElement())) {
                            visibleEnvelopes.getChildren().add(envelope.getElement());
                        }
                        Portamento pitchbend = portamentoMap.get(pos);
                        if (!visiblePitchbends.getChildren().contains(pitchbend.getElement())) {
                            visiblePitchbends.getChildren().add(pitchbend.getElement());
                        }
                    }
                }
            } else {
                // Removes all elements outside visible region.
                visibleNotes.getChildren().remove(note.getElement());
                if (envelopeMap.containsKey(pos) && portamentoMap.containsKey(pos)) {
                    visibleEnvelopes.getChildren().remove(envelopeMap.get(pos).getElement());
                    visiblePitchbends.getChildren().remove(portamentoMap.get(pos).getElement());
                }
            }
        }

        // Removes all elements not in visibleRegion.
    }

    boolean hasNote(int position) {
        return noteMap.containsKey(position);
    }

    Note getNote(int position) {
        return noteMap.get(position);
    }

    Collection<Note> getAllNotes() {
        return noteMap.values();
    }

    void putNote(int position, Note note) throws NoteAlreadyExistsException {
        if (noteMap.containsKey(position)) {
            throw new NoteAlreadyExistsException();
        }
        noteMap.put(position, note);
    }

    void addNoteElement(Note note) {
        allNotes.add(note);
        if (visibleRegion.intersects(note.getBounds())) {
            visibleNotes.getChildren().add(note.getElement());
        }
    }

    void removeNoteElement(Note note) {
        allNotes.remove(note);
        visibleNotes.getChildren().remove(note.getElement());
    }

    void removeFullNote(int position) {
        if (noteMap.containsKey(position)) {
            noteMap.remove(position);
        } else {
            // TODO: Handle this better.
            System.out.println("Could not find note in map of track notes :(");
        }
        if (envelopeMap.containsKey(position)) {
            visibleEnvelopes.getChildren().remove(envelopeMap.get(position).getElement());
            envelopeMap.remove(position);
        }
        if (portamentoMap.containsKey(position)) {
            visiblePitchbends.getChildren().remove(portamentoMap.get(position).getElement());
            portamentoMap.remove(position);
        }
    }

    void putEnvelope(int position, EnvelopeData envelopeData, EnvelopeCallback callback) {
        // Track note must exist before envelope is added.
        if (noteMap.containsKey(position)) {
            Envelope envelope =
                    envelopeFactory.createEnvelope(noteMap.get(position), envelopeData, callback);
            // Overrides are expected here.
            if (envelopeMap.containsKey(position)) {
                visibleEnvelopes.getChildren().remove(envelopeMap.get(position).getElement());
            }
            envelopeMap.put(position, envelope);
            if (visibleRegion.intersects(noteMap.get(position).getBounds())) {
                visibleEnvelopes.getChildren().add(envelope.getElement());
            }
        }
    }

    boolean hasEnvelope(int position) {
        return envelopeMap.containsKey(position);
    }

    Envelope getEnvelope(int position) {
        return envelopeMap.get(position);
    }

    void putPortamento(
            int position,
            String prevPitch,
            PitchbendData pitchbend,
            PortamentoCallback callback) {
        if (noteMap.containsKey(position)) {
            Portamento portamento = portamentoFactory
                    .createPortamento(noteMap.get(position), prevPitch, pitchbend, callback);
            // Overrides are expected here.
            if (portamentoMap.containsKey(position)) {
                visiblePitchbends.getChildren().remove(portamentoMap.get(position).getElement());
            }
            portamentoMap.put(position, portamento);
            if (visibleRegion.intersects(noteMap.get(position).getBounds())) {
                visiblePitchbends.getChildren().add(portamento.getElement());
            }
        }
    }

    boolean hasPortamento(int position) {
        return portamentoMap.containsKey(position);
    }

    Portamento getPortamento(int position) {
        return portamentoMap.get(position);
    }

    boolean isEmpty() {
        return noteMap.isEmpty() && envelopeMap.isEmpty() && portamentoMap.isEmpty();
    }
}
