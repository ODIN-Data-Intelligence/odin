import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import { bookmarkApi } from '@datacatalog/shared';

interface BookmarkButtonProps {
  datasetId: string;
  datasetTitle: string;
}

export default function BookmarkButton({ datasetId, datasetTitle }: BookmarkButtonProps) {
  const qc = useQueryClient();

  const { data: bookmark, isLoading } = useQuery({
    queryKey: ['bookmark-check', datasetId],
    queryFn: () => bookmarkApi.check(datasetId).catch(() => null),
    staleTime: 60_000,
    retry: false,
  });

  const add = useMutation({
    mutationFn: () => bookmarkApi.create({ datasetId, datasetTitle }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmark-check', datasetId] });
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
    },
  });

  const remove = useMutation({
    mutationFn: () => bookmarkApi.delete(bookmark!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmark-check', datasetId] });
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
    },
  });

  const isBookmarked = !!bookmark;
  const isBusy = isLoading || add.isPending || remove.isPending;

  function handleClick(e: React.MouseEvent) {
    e.stopPropagation();
    if (isBusy) return;
    isBookmarked ? remove.mutate() : add.mutate();
  }

  return (
    <Tooltip title={isBookmarked ? 'Remove bookmark' : 'Bookmark this dataset'}>
      <span>
        <IconButton
          size="small"
          onClick={handleClick}
          disabled={isBusy}
          color={isBookmarked ? 'warning' : 'default'}
        >
          {isBookmarked ? <StarIcon fontSize="small" /> : <StarBorderIcon fontSize="small" />}
        </IconButton>
      </span>
    </Tooltip>
  );
}
