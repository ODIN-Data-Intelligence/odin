import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bookmarkApi } from '@datacatalog/shared';

interface BookmarkButtonProps {
  datasetId: string;
  datasetTitle: string;
  className?: string;
}

export default function BookmarkButton({ datasetId, datasetTitle, className = '' }: BookmarkButtonProps) {
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
    <button
      onClick={handleClick}
      disabled={isBusy}
      title={isBookmarked ? 'Remove bookmark' : 'Bookmark this dataset'}
      className={`flex-shrink-0 text-lg leading-none transition-colors focus:outline-none ${
        isBookmarked
          ? 'text-amber-400 hover:text-amber-500'
          : 'text-gray-300 hover:text-amber-400'
      } ${isBusy ? 'opacity-50 cursor-wait' : 'cursor-pointer'} ${className}`}
    >
      {isBookmarked ? '★' : '☆'}
    </button>
  );
}
